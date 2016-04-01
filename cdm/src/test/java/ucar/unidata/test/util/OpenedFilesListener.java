package ucar.unidata.test.util;

import org.kohsuke.file_leak_detector.ActivityListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

/**
 * This class is notified by <a href ="http://file-leak-detector.kohsuke.org/">file-leak-detector</a> each time a file
 * is opened via {@link FileInputStream}, {@link FileOutputStream}, {@link RandomAccessFile}, or
 * {@link java.util.zip.ZipFile}. For every notification, a record of the file that was opened and a stack trace of
 * the method invocations that caused it are asynchronously written to a report file. The records are
 * {@link ObjectOutputStream serialized} and {@link GZIPOutputStream gzipped}.
 * <p/>
 * This class is thread-safe.
 *
 * @author cwardgar
 * @since 2016-03-14
 */
public class OpenedFilesListener extends ActivityListener {
    private final static Logger logger = LoggerFactory.getLogger(OpenedFilesListener.class);

    /**
     * The key to a system property that the user must set in order to configure the listener. Its value shall be
     * the {@link Path} to which the opened-file records shall be written. Set in testing.gradle.
     */
    public static final String REPORT_FILE_KEY = "OpenedFilesListener_report.file";

    /**
     * The set of {@link OpenOption}s that will be applied to the {@link AsynchronousFileChannel} that we're using to
     * write the opened-files report. The channel will:
     * <ul>
     *     <li>Create the file if it doesn't exist.</li>
     *     <li>Open the file for writing only.</li>
     *     <li>Truncate the file's length to zero if it already exists and has content.</li>
     * </ul>
     */
    private static final Set<StandardOpenOption> openOptions = new HashSet<>(Arrays.asList(
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));

    private final AsynchronousFileChannel channel;
    private final AtomicLong filePos;

    private final ByteArrayOutputStream byteOutStream;
    private final GZIPOutputStream gzipOutStream;
    private final ObjectOutputStream objOutStream;

    private final Object writeLock;

    /**
     * The nullary constructor that file-leak-detector will use to instantiate this class.
     *
     * @throws IOException  if an I/O error occurs.
     */
    public OpenedFilesListener() throws IOException {
        this(getSystemPropAsPath(REPORT_FILE_KEY));
    }

    /**
     * Gets the value of the system property as a Path.
     *
     * @param propKey  a system property key.
     * @return  the value of the system property as a Path, or {@code null} if no property with the specified key was
     *          found or the value is not a valid Path.
     */
    public static Path getSystemPropAsPath(String propKey) {
        String propVal = System.getProperty(propKey);
        if (propVal != null) {
            try {
                return Paths.get(propVal);
            } catch (InvalidPathException e) {
                // Continue below.
            }
        }

        return null;
    }

    /**
     * The constructor that all clients besides file-leak-detector should use.
     *
     * @param reportFile  the path to which the opened-file records shall be written.
     * @throws IOException  if an I/O error occurs.
     */
    public OpenedFilesListener(Path reportFile) throws IOException {
        Objects.requireNonNull(reportFile, "reportFile must be non-null");

        // Use a single worker thread operating off an unbounded queue for all writes. This class is heavily I/O-bound,
        // so using a pool with more than one thread wouldn't provide any benefit. Furthermore, such a pool couldn't
        // guarantee that tasks are executed sequentially, which we need for our writes to the output stream.
        this.channel = AsynchronousFileChannel.open(reportFile, openOptions, Executors.newSingleThreadExecutor());

        // Tracks the position in the report file of the next write operation. It technically doesn't need to be an
        // AtomicLong since all accesses of it are synchronized on writeLock, but the getAndAdd method is convenient.
        this.filePos = new AtomicLong();

        // We want to serialize and gzip the file-opened records, but Java only provides that functionality in the
        // form of OutputStreams. Furthermore, there is no OutputStream adapter that wraps AsynchronousFileChannel.
        // So, we will first transform our output using these streams and then write the resulting bytes to the channel.
        this.byteOutStream = new ByteArrayOutputStream();
        this.gzipOutStream = new GZIPOutputStream(byteOutStream);
        this.objOutStream  = new ObjectOutputStream(gzipOutStream);

        // An object that we will use to synchronize access to all shared mutable state: channel, filePos,
        // byteOutStream, gzipOutStream, and objOutStream.
        //
        // Ordinarily, AsynchronousFileChannel obviates the need for synchronization of writes. However, when a record
        // is transformed by ObjectOutputStream and GZIPOutputStream, the resulting bytes must appear earlier in the
        // output file than the bytes of all subsequent transformed records. Otherwise, the sequence of bytes will be
        // invalid and deserialization will fail. If not for that requirement, it wouldn't matter what order we wrote
        // the records.
        this.writeLock = new Object();

        // This class is intended to record file openings for the entire period that the JVM is active, so the only
        // appropriate place to close the report's FileChannel is at JVM shutdown.
        Runtime.getRuntime().addShutdownHook(new ShutdownThread());

        // Avoids an infinite recursion in open(Object, File). Without this line, the first time that open() was
        // invoked, the "new FullWriteCompletionHandler(src)" call would cause FullWriteCompletionHandler
        // to be loaded by the classloader, accomplished by reading the .class file with a FileInputStream.
        //
        // However, file-leak-detector has instrumented the FileInputStream constructor to call open() right before
        // it returns. Thus, the program would recurse into open(), but FullWriteCompletionHandler
        // *hadn't finished loading*. And so, when the program reached the "new FullWriteCompletionHandler(src)" call
        // again, another attempt would be made to load the class, since the last one hadn't finished. The
        // recursion would continue, ad infinitum.
        //
        // By pre-loading the class here in the constructor, the "new FullWriteCompletionHandler(src)" call in open()
        // won't result in a file being opened.
        try {
            getClass().getClassLoader().loadClass(FullWriteCompletionHandler.class.getName());
        } catch (ClassNotFoundException e) {
            throw new AssertionError("CAN'T HAPPEN: FullWriteCompletionHandler is defined below.", e);
        }
    }

    /** A shutdown hook that closes the FileChannel, writing out any bytes that remain in the output streams first. */
    private final class ShutdownThread extends Thread {
        @Override public void run() {
            synchronized (writeLock) {
                try {
                    try {
                        objOutStream.close();
                    } catch (IOException e) {
                        throw new AssertionError("CAN'T HAPPEN: Our use of ObjectOutputStream, GZIPOutputStream, " +
                                "and ByteArrayOutputStream should never result in IOExceptions, as all writes are " +
                                "in-memory and all objects written are serializable.", e);
                    }

                    // The close may have flushed more bytes to byteOutStream.
                    byte[] remainingBytes = byteOutStream.toByteArray();

                    if (remainingBytes.length > 0) {
                        ByteBuffer src = ByteBuffer.wrap(remainingBytes);
                        long position = filePos.getAndAdd(remainingBytes.length);

                        try {
                            // Write data and block until finished.
                            // We must block to avoid closing the channel before the write has finished.
                            int bytesWritten = channel.write(src, position).get();

                            if (bytesWritten == remainingBytes.length) {
                                logger.info(String.format("Wrote %s remaining bytes at shutdown.", bytesWritten));
                            } else {
                                logger.error(String.format("Wrote %s of %s remaining bytes at shutdown.",
                                        bytesWritten, remainingBytes.length));
                            }
                        } catch (InterruptedException | ExecutionException e) {
                            logger.warn("Failed to write to AsynchronousFileChannel at shutdown.", e);
                        }
                    }
                } finally {
                    try {
                        // May result in the writer thread throwing an AsynchronousCloseException if it's still trying
                        // to write when we close.
                        // LOOK: Is this a real concern in practice? If so, one way to guarantee that all writes have
                        // finished before we close is to submit another write task to the worker thread in this
                        // method (even if it's a fake record) and block until it's finished. Then close.
                        // The single-thread pool that we're using guarantees sequential task processing, so all
                        // pending writes will finish before the final fake record is written.
                        // Or: http://niklasschlimm.blogspot.com/2012/05/java-7-9-nio2-file-channels-on-test.html
                        channel.close();
                    } catch (IOException e) {
                        logger.warn("Failed to close AsynchronousFileChannel at shutdown.", e);
                    }
                }
            }
        }
    }

    @Override
    public void open(Object obj, File file) {
        if (!Files.isRegularFile(file.toPath())) {
            // We're not interested in special files. /dev/random, in particular, causes infinite recursion if we don't
            // exclude it here because ObjectOutputStream.writeObject() opens it.
            return;
        }

        synchronized (writeLock) {
            // We only close the FileChannel at JVM shutdown, so it should still be open in virtually any circumstance
            // that this method is called. A potential exception is JaCoCo, explained below.
            if (channel.isOpen()) {
                byte[] recordBytes = getSerializedGZippedBytes(file, new Exception().getStackTrace());
                ByteBuffer src = ByteBuffer.wrap(recordBytes);
                long position = filePos.getAndAdd(recordBytes.length);

                channel.write(src, position, position, new FullWriteCompletionHandler(src));
            } else {
                if (file.getAbsolutePath().endsWith("test.exec")) {
                    // JaCoCo registers a shutdown hook to write out coverage data to a file when the JVM terminates
                    // (test.exec). That hook may run after ours does, meaning that channel will be closed by the time
                    // this method is called for the coverage data record. There's not a whole lot we can do about
                    // that, but it is an expected occurrence.
                    logger.debug("Couldn't write file-opened record for the JaCoCo coverage data file.");
                } else {
                    logger.warn(String.format("Couldn't write file-opened record for: %s", file));
                }
            }
        }
    }

    /**
     * Takes a file-opened record (comprised of {@code file} and {@code stackTraceElems}), transforms it via
     * {@link ObjectOutputStream} and {@link GZIPOutputStream}, and returns the array of bytes that results.
     *
     * @param file  the file that was opened.
     * @param stackTraceElems a stack trace that can be used to identify what class is responsible for opening the file.
     * @return  the bytes of the serialized, gzipped record.
     */
    // This method is private and only called from within synchronized blocks, so it doesn't need any additional
    // synchronization of its own.
    private byte[] getSerializedGZippedBytes(File file, StackTraceElement[] stackTraceElems) {
        try {
            // Serializes and gzips the record. The result will eventually be flushed to underlying byteOutStream.
            objOutStream.writeObject(file);
            objOutStream.writeObject(stackTraceElems);
        } catch (IOException e) {
            throw new AssertionError("CAN'T HAPPEN: Our use of ObjectOutputStream, GZIPOutputStream, " +
                    "and ByteArrayOutputStream should never result in IOExceptions, as all writes are " +
                    "in-memory and all objects written are serializable.", e);
        }

        // Steal the resulting bytes from byteOutStream and return them. Note that ret only contains the bytes that
        // have been flushed to byteOutStream; other bytes that are part of the record may still be stored in the
        // internal buffers of objOutStream and gzipOutStream. That's okay: we're mostly just ensuring that
        // byteOutStream's buffer doesn't grow too large as we write more records. And of course, those bytes will
        // eventually be written, either in a subsequent write() or a close().
        byte[] ret = byteOutStream.toByteArray();
        byteOutStream.reset();
        return ret;
    }

    /**
     * A callback that is executed asynchronously on the file channel's thread pool after the completion or failure of
     * a write operation.
     */
    private final class FullWriteCompletionHandler implements CompletionHandler<Integer, Long> {
        private final ByteBuffer byteBuffer;

        private FullWriteCompletionHandler(ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
        }

        @Override
        public void completed(Integer bytesWritten, Long filePosition) {
            logger.debug(String.format("For record at position %s: wrote %s of %s bytes.",
                    filePosition, bytesWritten, byteBuffer.limit()));

            if (byteBuffer.hasRemaining()) {
                // AsynchronousFileChannel.write() does not guarantee that it will write all of the data requested:
                // http://niklasschlimm.blogspot.de/2012/05/java-7-10-nio2-file-channels-on-test.html
                // So, in the event of a partial write, recursively call write() for the remaining bytes.
                // I'm not sure if this will ever happen in practice, so log an INFO message about it.
                logger.info(String.format("For record at position %s: Writing the %s remaining bytes next.",
                        filePosition, byteBuffer.remaining()));

                long nextFilePos = filePosition + bytesWritten;
                channel.write(byteBuffer, nextFilePos, nextFilePos, this);
            }
        }

        @Override
        public void failed(Throwable exc, Long filePosition) {
            logger.error(String.format("Failed to write record at position %s.", filePosition), exc);
        }
    }
}
