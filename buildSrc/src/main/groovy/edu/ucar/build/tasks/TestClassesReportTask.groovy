package edu.ucar.build.tasks

import com.google.common.collect.Iterables
import org.apache.commons.io.FilenameUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Creates a report of all the test classes in a project. Both Java and Groovy test classes are included.
 * The report is written to {@link #getDestFile}.
 *
 * @author cwardgar
 * @since 2016-03-16
 */
class TestClassesReportTask extends DefaultTask {
    @OutputFile
    File destFile

    // I can't make these private for some reason:
    //   "Could not find property 'hasJava' on task ':cdm:testClassesReport'."
    boolean hasJava
    boolean hasGroovy

    TestClassesReportTask() {
        destFile = project.file("${project.buildDir}/reports/testClasses.txt")
        hasJava = project.plugins.hasPlugin('java')
        hasGroovy = project.plugins.hasPlugin('groovy')

        if (hasJava) {
            inputs.sourceDir project.sourceSets.test.java.srcDirs
        }
        if (hasGroovy) {
            inputs.sourceDir project.sourceSets.test.groovy.srcDirs
        }
    }

    @TaskAction
    def run() {
        destFile.parentFile.mkdirs();

        destFile.withPrintWriter { PrintWriter writer ->
            if (hasJava) {
                writeSourceFileClassNames writer, project.sourceSets.test.java
            }
            if (hasGroovy) {
                writeSourceFileClassNames writer, project.sourceSets.test.groovy
            }
        }
    }

    static void writeSourceFileClassNames(PrintWriter writer, SourceDirectorySet srcDirSet) {
        File srcDir = Iterables.getOnlyElement(srcDirSet.srcDirs)

        srcDirSet.files.each { File srcFile ->
            writer.println getFullyQualifiedClassName(srcDir, srcFile)
        }
    }

    static String getFullyQualifiedClassName(File srcDir, File srcFile) {
        // e.g. "/Users/cwardgar/dev/projects/thredds2/cdm/src/test/java/"
        String srcDirPath = srcDir.absolutePath
        if (!srcDirPath.endsWith('/')) {
            srcDirPath += '/'  // Append trailing backslash if it doesn't have one already.
        }

        // e.g. "/Users/cwardgar/dev/projects/thredds2/cdm/src/test/java/ucar/ma2/ArrayTest.java"
        String srcFilePath = srcFile.absolutePath

        assert srcFilePath.startsWith(srcDirPath) : "$srcDir does not contain $srcFile"

        // e.g. "ucar/ma2/ArrayTest.java"
        String srcFileRelPath = srcFilePath.substring(srcDirPath.length())

        // e.g. "ucar/ma2/ArrayTest"
        String srcFileRelPathNoExt = FilenameUtils.removeExtension(srcFileRelPath)

        // e.g. "ucar.ma2.ArrayTest"
        String fullyQualifiedClassName = srcFileRelPathNoExt.replace('/', '.')

        return fullyQualifiedClassName
    }
}
