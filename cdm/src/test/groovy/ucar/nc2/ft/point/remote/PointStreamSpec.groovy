package ucar.nc2.ft.point.remote

import org.apache.commons.io.FilenameUtils
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll
import ucar.nc2.ft.FeatureDatasetPoint
import ucar.nc2.ft.PointFeatureCollection
import ucar.nc2.ft.point.FlattenedPointCollection
import ucar.unidata.util.point.PointTestUtil
import ucar.unidata.util.test.TestDir

/**
 * @author cwardgar
 * @since 2015/09/21
 */
class PointStreamSpec extends Specification {
    public static final String cfDocDsgExamplesDir = TestDir.cdmLocalTestDataDir + "cfDocDsgExamples/";
    public static final String pointDir = TestDir.cdmLocalTestDataDir + "point/";
    
    @Rule TemporaryFolder tempFolder = new TemporaryFolder()

    @Unroll  // Method will have its iterations reported independently.
    def "round trip['#location']"() {
        setup:
        File outFile = tempFolder.newFile(FilenameUtils.getBaseName(location) + ".bin")
        FeatureDatasetPoint fdPoint = PointTestUtil.openPointDataset(location)

        when:
        PointFeatureCollection origPointCol = new FlattenedPointCollection(fdPoint.pointFeatureCollectionList);
        PointStream.write(origPointCol, outFile);
        PointFeatureCollection roundTrippedPointCol = new PointCollectionStreamLocal(outFile);

        then:
        PointTestUtil.assertEquals(origPointCol, roundTrippedPointCol)

        cleanup:
        roundTrippedPointCol.finish()
        origPointCol.finish()
        fdPoint.close()

        where:
        location << [
                cfDocDsgExamplesDir + "H.1.1.ncml",
                pointDir + "point.ncml",
                pointDir + "pointMissing.ncml",
                pointDir + "pointUnlimited.nc"
        ]
    }
}
