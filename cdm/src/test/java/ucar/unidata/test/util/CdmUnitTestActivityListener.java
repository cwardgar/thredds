package ucar.unidata.test.util;

import org.kohsuke.file_leak_detector.ActivityListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @author cwardgar
 * @since 2016-03-14
 */
public class CdmUnitTestActivityListener extends ActivityListener {
    private final static Path testdataPath   = Paths.get("/Users/cwardgar/dev/projects/thredds2/testdata.txt");
    private final static Path cdmUnitTestDir = Paths.get(System.getProperty("unidata.testdata.path"), "cdmUnitTest");

    public void open(Object obj, File file) {
        if (!file.toPath().startsWith(cdmUnitTestDir)) {
            return;
        }

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

        try {
            Files.write(testdataPath, lines, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final List<String> boringMethodPrefixes = Arrays.asList(
        "ucar.unidata.test.util.CdmUnitTestActivityListener.",
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
