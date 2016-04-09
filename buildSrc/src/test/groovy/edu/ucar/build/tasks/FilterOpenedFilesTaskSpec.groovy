package edu.ucar.build.tasks

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.SimpleFileCollection
import spock.lang.Shared
import spock.lang.Specification

/**
 *
 *
 * @author cwardgar
 * @since 2016-04-06
 */
class FilterOpenedFilesTaskSpec extends Specification {
    def "getFullyQualifiedClassNames"() {
        setup:
        def files = [
                '/thredds/cdm/src/main/java/ucar/nc2/constants/CF.java' as File,
                '/Users/cwardgar/dev/projects/thredds2/tds/src/main/resources/Godiva3.gwt.xml' as File,
                '/thredds/cdm/src/test/groovy/ucar/ma2/StructureMembersSpec.groovy' as File,
        ]
        FileCollection fileColl = new SimpleFileCollection(files)

        def expectedClassNames = [ 'ucar.nc2.constants.CF', 'ucar.ma2.StructureMembersSpec' ] as Set

        expect:
        FilterOpenedFilesTask.getFullyQualifiedClassNames(fileColl) == expectedClassNames
    }

    @Shared StackTraceElement[] stackFrames = [
            new StackTraceElement('ucar.unidata.io.RandomAccessFile',  'readBuffer', 'RandomAccessFile.java', 520),
            new StackTraceElement('ucar.nc2.iosp.hdf5.DataBTree$Node', '<init>',     'DataBTree.java',        170),
            new StackTraceElement('ucar.nc2.NetcdfFile',               'readData',   'NetcdfFile.java',       2009),
            new StackTraceElement('thredds.server.opendap.NcSDArray',  'read',       'NcSDArray.java',        115),
            new StackTraceElement('javax.servlet.http.HttpServlet',    'service',    'HttpServlet.java',      621),
    ]

    def 'findNearestMethodCallFrom NetcdfFile'() {
        setup:
        def classNames = [ 'javax.servlet.http.HttpServlet', 'ucar.nc2.NetcdfFile' ] as Set

        expect: 'The NetcdfFile frame'
        FilterOpenedFilesTask.findNearestMethodCallFromOneOf(stackFrames, classNames) == stackFrames[2]
    }

    def 'findNearestMethodCallFrom nested class'() {
        setup:
        def classNames = [ 'thredds.server.opendap.NcSDArray', 'ucar.nc2.iosp.hdf5.DataBTree' ] as Set

        expect: 'The DataBTree$Node frame'
        FilterOpenedFilesTask.findNearestMethodCallFromOneOf(stackFrames, classNames) == stackFrames[1]
    }

    def 'findNearestMethodCallFromOneOf failure'() {
        setup:
        def classNames = [ 'ucar.nc2.Variable', 'opendap.servlet.AsciiWriter', 'java.lang.Thread' ] as Set

        expect: "stackFrames doesn't contain any of the classes"
        FilterOpenedFilesTask.findNearestMethodCallFromOneOf(stackFrames, classNames) == null
    }
}
