package ucar.unidata.test.util;

import org.kohsuke.file_leak_detector.ActivityListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author cwardgar
 * @since 2016-03-14
 */
public class OpenedFilesListener extends ActivityListener {
    public static final String INCLUDED_PATHS_FILE_KEY = "OpenedFilesListener_included.paths.file";
    public static final String INCLUDED_CLASSES_FILE_KEY = "OpenedFilesListener_included.classes.file";
    public static final String REPORT_FILE_KEY = "OpenedFilesListener_report.file";

    private final Path includedPathsFile;
    private final Path includedClassesFile;
    private final Path reportFile;

    public OpenedFilesListener() throws IOException {
        this(getSystemPropAsPath(INCLUDED_PATHS_FILE_KEY), getSystemPropAsPath(INCLUDED_CLASSES_FILE_KEY),
             getSystemPropAsPath(REPORT_FILE_KEY));
    }

    public OpenedFilesListener(Path includedPathsFile, Path includedClassesFile, Path reportFile) throws IOException {
        this.includedPathsFile = includedPathsFile;
        this.includedClassesFile = includedClassesFile;

        this.reportFile = Objects.requireNonNull(reportFile, "reportFile must be non-null");
        Files.deleteIfExists(reportFile);
        Files.createFile(reportFile);
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

    private static List<String> readFileAtSystemProp(String propKey) {
        Path path = Paths.get(System.getProperty(propKey));
        try {
            return Files.readAllLines(path);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public void open(Object obj, File file) {
        //if (!file.toPath().startsWith(cdmUnitTestDir)) {
        //    return;
        //}

        List<String> lines = new LinkedList<>();
        lines.add(String.format("OPEN %s: %s", obj.getClass().getSimpleName(), file.getAbsolutePath()));

        Exception exception = new Exception();

        StackTraceElement elem = identifyTestMethod(exception);
        if (elem != null) {
            lines.add(elem.getClassName() + "." + elem.getMethodName());
        } else {
            lines.add(getStackTraceString(exception).trim());
        }

        lines.add("");

        //try {
        //    Files.write(testdataPath, lines, StandardOpenOption.APPEND);
        //} catch (IOException e) {
        //    e.printStackTrace();
        //}
    }

    private final List<String> boringMethodPrefixes = Arrays.asList(
        "ucar.unidata.test.util.OpenedFilesListener.",
        "org.kohsuke.file_leak_detector.Listener.",
        "sun.",
        "java.",
        "ucar.unidata.io.RandomAccessFile.",
        "ucar.nc2.NetcdfFile.",
        "ucar.nc2.iosp.hdf5.TestH5.open",
        "ucar.nc2.util.cache.FileCache.",
        "ucar.nc2.grib.grib1.Grib1Index.",
        "ucar.nc2.grib.grib2.Grib2Index.",
        "ucar.nc2.grib.collection.Grib1CollectionWriter.",
        "ucar.nc2.grib.collection.Grib2CollectionWriter.",
        "ucar.nc2.grib.collection.Grib2CollectionBuilder.",
        "ucar.nc2.grib.collection.GribPartitionBuilder.",
        "ucar.nc2.grib.collection.GribCdmIndex."
    );

    private StackTraceElement identifyTestMethod(Exception exception) {
outer:  for (StackTraceElement elem : exception.getStackTrace()) {
            for (String methodPrefix : boringMethodPrefixes) {
                String fullMethodName = elem.getClassName() + "." + elem.getMethodName();
                if (fullMethodName.startsWith(methodPrefix)) {
                    continue outer;
                }
            }

            return elem;
        }

        return null;  // All method invocations in the stack trace started with a boringMethodPrefix.
    }

    private static String getStackTraceString(Exception exception) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);

        exception.printStackTrace(printStream);
        return byteArrayOutputStream.toString();
    }
}
