package ucar.nc2.ft.point

import org.junit.Assert
import spock.lang.Specification
import ucar.ma2.DataType
import ucar.ma2.StructureDataScalar
import ucar.nc2.constants.FeatureType
import ucar.nc2.ft.DsgFeatureCollection
import ucar.nc2.ft.FeatureDatasetPoint
import ucar.nc2.ft.PointFeatureCollection
import ucar.nc2.time.CalendarDateUnit
import ucar.unidata.util.point.PointTestUtil

/**
 * @author cwardgar
 * @since 2015-11-02
 */
class SortingPointFeatureCollectionSpec extends Specification {
    def "features by hand, different sort methods, simple comparator"() {
        setup: "Create SimplePointFeatureCollection"
        StructureDataScalar stationData = new StructureDataScalar("StationFeature")  // leave it empty.
        stationData.addMemberString("name", null, null, "Foo", 3)
        stationData.addMemberString("desc", null, null, "Bar", 3)
        stationData.addMemberString("wmoId", null, null, "123", 3)
        stationData.addMember("lat", null, "degrees_north", DataType.DOUBLE, 30)
        stationData.addMember("lon", null, "degrees_east", DataType.DOUBLE, 60)
        stationData.addMember("alt", null, "meters", DataType.DOUBLE, 5000)

        StationFeature stationFeat = new StationFeatureImpl("Foo", "Bar", "123", 30, 60, 5000, 4, stationData)

        CalendarDateUnit timeUnit = CalendarDateUnit.of(null, "days since 1970-01-01")
        DsgFeatureCollection dummyDsg = new SimplePointFeatureCC("dummy", timeUnit, "m", FeatureType.STATION)

        SimplePointFeatureCollection simplePfc = new SimplePointFeatureCollection("simplePfc", timeUnit, "m")
        simplePfc.add makeStationPointFeature(dummyDsg, stationFeat, timeUnit, 10, 10, 103)
        simplePfc.add makeStationPointFeature(dummyDsg, stationFeat, timeUnit, 20, 20, 96)
        simplePfc.add makeStationPointFeature(dummyDsg, stationFeat, timeUnit, 30, 30, 118)
        simplePfc.add makeStationPointFeature(dummyDsg, stationFeat, timeUnit, 40, 40, 110)

        and: "Create comparator that will sort elements in reverse order of obs time."
        def revObsTimeComp = new OrderBy({ it.observationTime }).reversed()

        and: "sortingPfc sorts simplePfc using revObsTimeComp"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection(simplePfc, revObsTimeComp)

        expect: "Sorted list and SortingPointFeatureCollection have same iteration order when using same comparator."
        PointTestUtil.assertIterablesEquals simplePfc.asList().toSorted(revObsTimeComp), sortingPfc.asList()
        
        cleanup:
        simplePfc?.finish()
    }

    private static StationPointFeature makeStationPointFeature(DsgFeatureCollection dsg, StationFeature stationFeat,
            CalendarDateUnit timeUnit, double obsTime, double nomTime, double tasmax) {
        StructureDataScalar featureData = new StructureDataScalar("StationPointFeature")
        featureData.addMember("obsTime", "Observation time", timeUnit.getUdUnit(), DataType.DOUBLE, obsTime)
        featureData.addMember("nomTime", "Nominal time", timeUnit.getUdUnit(), DataType.DOUBLE, nomTime)
        featureData.addMember("tasmax", "Max temperature", "Celsius", DataType.DOUBLE, tasmax)

        return new SimpleStationPointFeature(dsg, stationFeat, obsTime, nomTime, timeUnit, featureData)
    }

    def "features from file, different sort methods, simple comparator"() {
        setup: "Open test file and flatten the PFCs within"
        FeatureDatasetPoint fdPoint = PointTestUtil.openClassResourceAsPointDataset(getClass(), "orthogonal.ncml")
        FlattenedPointCollection flattenedPfc = new FlattenedPointCollection(fdPoint.pointFeatureCollectionList)

        and: "Create comparator that will sort elements in reverse order of station name."
        def revStationNameComp = SortingPointFeatureCollection.stationNameComparator.reversed()

        and: "sortingPfc sorts simplePfc using revStationNameComp"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection(flattenedPfc, revStationNameComp)

        expect: "Sorted list and SortingPointFeatureCollection have same iteration order when using same comparator."
        PointTestUtil.assertIterablesEquals flattenedPfc.asList().toSorted(revStationNameComp), sortingPfc.asList()

        cleanup:
        flattenedPfc?.finish()
        fdPoint?.close()
    }
    
    def "features from file, value comparison, complex comparator"() {
        setup: "Open the input file and flatten the PFCs within"
        FeatureDatasetPoint fdPointInput =
                PointTestUtil.openClassResourceAsPointDataset(getClass(), "cacheTestInput1.ncml")
        FlattenedPointCollection inputPfc =
                new FlattenedPointCollection(fdPointInput.pointFeatureCollectionList)
    
        and: "Create first comparator. It orders by observation time"
        def obsTimeComp = new OrderBy({ it.observationTime })
    
        and: "Reverse order of the length of the station's name, e.g. '&&' < '&&&&, but '11' == '22'"
        def revStationNameLenComp = new OrderBy({ (it as StationPointFeature).station.name.length() }).reversed()
    
        and: "Combine the comparators. First use obsTimeComp, then break ties with revStationNameLenComp"
        def compositeComp = obsTimeComp.thenComparing revStationNameLenComp
        
        and: "Dump input into SortingPointFeatureCollection"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection(inputPfc, compositeComp)
        
        
        expect: "same station names"
        sortingPfc.collect({ (it as StationPointFeature).station.name }).unique() == [
            '7777777', '666666', '55555', '4444', '333', '22', '1' ]
        
        and: "same observation times"
        // Builds the list [ 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3 ]
        // I wanted to see how obnoxious I could be with Groovy though. Pretty damn obnoxious, it turns out.
        def expectedTimes = (1..3).collectMany { time -> (1..7).collect { time } }
        sortingPfc.collect { it.observationTime } == expectedTimes
        
        and: "same humidities"
        def expectedHumidities = (1..3).collectMany { time -> (7..1).collect { it + time * 0.1 } }
        def actualHumidities = sortingPfc.collect { it.featureData.getScalarFloat("humidity") }
        Assert.assertArrayEquals(expectedHumidities as float[], actualHumidities as float[], 0.01)


        cleanup: "Close or finish resources, in reverse order that they were acquired"
        sortingPfc?.finish()
        inputPfc?.finish()
        fdPointInput?.close()
    }
}
