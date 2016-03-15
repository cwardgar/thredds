package ucar.unidata.test.util;

import org.kohsuke.file_leak_detector.ActivityListener;

import java.io.File;

/**
 * @author cwardgar
 * @since 2016-03-14
 */
public class CdmUnitTestActivityListener extends ActivityListener {
    private final File cdmUnitTestDir = new File(System.getProperty("unidata.testdata.path"), "cdmUnitTest");

    public void open(Object obj, File file) {
        System.out.printf("OPEN %s: %s%n", obj.getClass().getSimpleName(), file.getAbsolutePath());
    }
}
