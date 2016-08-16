package ucar.nc2.ft.point

import spock.lang.Specification
import ucar.nc2.ft.FeatureDatasetPoint
import ucar.unidata.util.point.PointTestUtil
import ucar.unidata.util.test.TestDir

/**
 * Each of these feature methods corresponds to a Table subtype that produced unexpected results when an attempt
 * was made to cache PointFeatures. Specifically, all cached PointFeatures assumed the same featureData values
 * as that of the last PointFeature returned by the iterator.
 *
 * @author cwardgar
 * @since 2016-08-16
 */
class CachingPointFeaturesSpec extends Specification {
    def "TableMultidimInner"() {
        setup: "Open test file and flatten the PFCs within"
        String location = TestDir.cdmLocalTestDataDir + "point/stationMultidimUnlimited.nc"
        FeatureDatasetPoint fdPoint = PointTestUtil.openPointDataset(location)
        FlattenedPointCollection flattenedPfc = new FlattenedPointCollection(fdPoint.pointFeatureCollectionList)
        
        when:
        def expectedPointFeatTimes = (0..14).collect { it * 10.0 }
        def actualPointFeatTimes = flattenedPfc.asList().collect { it.featureData.getScalarDouble("time") }
        
        then:
        expectedPointFeatTimes == actualPointFeatTimes
        
        cleanup:
        flattenedPfc?.finish()
        fdPoint?.close()
    }
    
    def "TableMultidimInner3D"() {
        setup: "Open test file and flatten the PFCs within"
        String location = TestDir.cdmLocalTestDataDir + "point/stationProfileMultidimUnlimited.nc"
        FeatureDatasetPoint fdPoint = PointTestUtil.openPointDataset(location)
        FlattenedPointCollection flattenedPfc = new FlattenedPointCollection(fdPoint.pointFeatureCollectionList)
    
        when:
        def expectedPointFeatTimes = (0..17).collect { it * 2.0 + 1.0 }
        def actualPointFeatTimes = flattenedPfc.asList().collect { it.featureData.getScalarDouble("time") }
        
        then:
        expectedPointFeatTimes == actualPointFeatTimes
    
        cleanup:
        flattenedPfc?.finish()
        fdPoint?.close()
    }
    
    def "TableMultidimInnerPsuedo"() {
        setup: "Open test file and flatten the PFCs within"
        String location = TestDir.cdmLocalTestDataDir + "cfDocDsgExamples/H.2.1.1.ncml"
        FeatureDatasetPoint fdPoint = PointTestUtil.openPointDataset(location)
        FlattenedPointCollection flattenedPfc = new FlattenedPointCollection(fdPoint.pointFeatureCollectionList)
    
        when:
        def expectedPointFeatHumidities = 1f..50f
        def actualPointFeatHumidities = flattenedPfc.asList().collect { it.featureData.getScalarFloat("humidity") }
        
        then:
        expectedPointFeatHumidities == actualPointFeatHumidities
    
        cleanup:
        flattenedPfc?.finish()
        fdPoint?.close()
    }
    
    def "TableMultidimInnerPsuedo3D"() {
        setup: "Open test file and flatten the PFCs within"
        String location = TestDir.cdmLocalTestDataDir + "cfDocDsgExamples/H.5.1.2.ncml"
        FeatureDatasetPoint fdPoint = PointTestUtil.openPointDataset(location)
        FlattenedPointCollection flattenedPfc = new FlattenedPointCollection(fdPoint.pointFeatureCollectionList)
    
        when:
        def expectedPointFeatHumidities = 1f..60f
        def actualPointFeatHumidities = flattenedPfc.asList().collect { it.featureData.getScalarFloat("humidity") }
    
        then:
        expectedPointFeatHumidities == actualPointFeatHumidities
    
        cleanup:
        flattenedPfc?.finish()
        fdPoint?.close()
    }
}
