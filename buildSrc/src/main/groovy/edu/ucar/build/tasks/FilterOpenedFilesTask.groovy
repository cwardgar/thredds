package edu.ucar.build.tasks

import org.apache.commons.io.FilenameUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream

/**
 *
 *
 * @author cwardgar
 * @since 2016-04-01
 */
class FilterOpenedFilesTask extends DefaultTask {
    static Logger log = LoggerFactory.getLogger(FilterOpenedFilesTask)

    @InputFile
    File unfilteredRecordsFile

    @Input
    FileCollection openedFilesToReportOn

    @Input
    FileCollection sourceFilesToReportOn

    @OutputFile
    File hitsDestFile

    @OutputFile
    File missesDestFile

    FilterOpenedFilesTask() {
        description = "Filters an opened-files report to only contain records of interest."

        onlyIf {
            if (unfilteredRecordsFile.exists()) {
                true
            } else {
                log.info "Skipping $name because unfiltered record file doesn't exist: $unfilteredRecordsFile"
                false
            }
        }
    }

    void addOpenedFilesToReportOn(Object... paths) {
        if (!openedFilesToReportOn) {
            openedFilesToReportOn = project.files(paths)
        } else {
            openedFilesToReportOn = openedFilesToReportOn + project.files(paths)
        }
    }

    void addSourceFilesToReportOn(Object... paths) {
        if (!sourceFilesToReportOn) {
            sourceFilesToReportOn = project.files(paths)
        } else {
            sourceFilesToReportOn = sourceFilesToReportOn + project.files(paths)
        }
    }

    @TaskAction
    def filter() {
        Set<String> classNames = getFullyQualifiedClassNames(sourceFilesToReportOn)

        new ObjectInputStream(new GZIPInputStream(unfilteredRecordsFile.newInputStream())).withCloseable {
            hitsDestFile.withPrintWriter { PrintWriter hitsWriter ->
                missesDestFile.withPrintWriter { PrintWriter missesWriter ->
                    int numRecords = 0

                    for (; true; ++numRecords) {
                        try {
                            File file = it.readObject() as File
                            StackTraceElement[] stackFrames = it.readObject() as StackTraceElement[]
                            StackTraceElement stackFrame = findNearestMethodCallFromOneOf(stackFrames, classNames)

                            if (stackFrame != null && openedFilesToReportOn.contains(file)) {
                                hitsWriter.printf("%s,%s.%s%n", file, stackFrame.className, stackFrame.methodName)
                            } else {
                                missesWriter.println file

                                if (stackFrame != null) {
                                    // If we've identified a frame-of-interest, print only that frame.
                                    missesWriter.printf "\tat %s%n", stackFrame
                                } else {
                                    // Otherwise print the entire stack trace.
                                    stackFrames.each {
                                        missesWriter.printf "\tat %s%n", it
                                    }
                                }
                            }
                        } catch (EOFException e) {  // This is the only way to detect EOF.
                            break
                        }
                    }

                    log.info "Deserialized $numRecords records"
                }
            }
        }
    }

    /**
     * Returns the fully-qualified names of the classes defined in a collection of source code files.
     * Any element in {@code sourceFiles} that is determined not to be a Java or Groovy source code file will be
     * skipped.
     *
     * @param sourceFiles  Java and Groovy source code files.
     */
    static Set<String> getFullyQualifiedClassNames(FileCollection sourceFiles) {
        Pattern srcFilePathPattern = ~'.*/src/(main|test)/(java|groovy)/(.*)'
        Set<String> classNames = new TreeSet<>();

        sourceFiles.files.each {
            // Convert to forward slashes to make regex easier to use.
            // e.g. "/Users/cwardgar/dev/projects/thredds2/cdm/src/test/java/ucar/ma2/ArrayTest.java"
            String absolutePath = FilenameUtils.separatorsToUnix(it.absolutePath)

            Matcher matcher = srcFilePathPattern.matcher(absolutePath)
            if (matcher.matches()) {
                // e.g. "ucar/ma2/ArrayTest.java"
                String srcFileRelPath = matcher.group(3);

                // e.g. "ucar/ma2/ArrayTest"
                String srcFileRelPathNoExt = FilenameUtils.removeExtension(srcFileRelPath)

                // e.g. "ucar.ma2.ArrayTest"
                String fullyQualifiedClassName = srcFileRelPathNoExt.replace('/', '.')

                classNames << fullyQualifiedClassName
            } else {
                log.warn("Path '{}' did not match the pattern '{}'.", absolutePath, srcFilePathPattern.pattern())
            }
        }

        return classNames
    }

    /**
     * Finds the nearest (i.e. top-most) frame in {@code stackFrames} that contains an invocation of a method defined
     * in one of the specified classes.
     *
     * @param stackFrames  a stack trace.
     * @param classNames  the names of classes whose method invocations are of interest. Note that nested classes of
     *                    those named in this set are also considered interesting.
     * @return  a "frame-of-interest" that is nearest to the top of the stack.
     */
    static StackTraceElement findNearestMethodCallFromOneOf(StackTraceElement[] stackFrames, Set<String> classNames) {
        for (StackTraceElement stackFrame : stackFrames) {
            for (String className : classNames) {
                // classNames coming from getFullyQualifiedClassNames() will only include the top-level class defined
                // in a source file, not nested classes. So to support stack frames from nested classes, we use
                // String.startsWith() instead of String.equals().
                // For example, if className=='TestDir' and stackFrame.className=='TestDir$Act', we have a match and
                // that frame will be returned.
                if (stackFrame.className.startsWith(className)) {
                    return stackFrame;
                }
            }
        }

        return null
    }
}
