package ucar.nc2.ft.point

import com.google.common.collect.Ordering
import spock.lang.IgnoreRest
import spock.lang.Specification
import ucar.ma2.DataType
import ucar.ma2.StructureDataScalar
import ucar.nc2.constants.FeatureType
import ucar.nc2.ft.DsgFeatureCollection
import ucar.nc2.ft.FeatureDatasetPoint
import ucar.nc2.ft.PointFeatureCollection
import ucar.nc2.time.CalendarDateUnit

/**
 * @author cwardgar
 * @since 2015-11-02
 */
class SortingPointFeatureCollectionSpec extends Specification {
    def "test1"() {
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
        Comparator<StationPointFeature> revObsTimeComp = new Comparator<StationPointFeature>() {
            @Override
            public int compare(StationPointFeature left, StationPointFeature right) {
                return -Double.compare(left.getObservationTime(), right.getObservationTime());
            }
        };

        and: "sortingPfc sorts simplePfc using revObsTimeComp"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection(simplePfc, revObsTimeComp)

        expect: "Sorted list and SortingPointFeatureCollection have same iteration order when using same comparator."
        PointTestUtil.assertIterablesEquals simplePfc.asList().toSorted(revObsTimeComp), sortingPfc.asList()
    }

    private static StationPointFeature makeStationPointFeature(DsgFeatureCollection dsg, StationFeature stationFeat,
            CalendarDateUnit timeUnit, double obsTime, double nomTime, double tasmax) {
        StructureDataScalar featureData = new StructureDataScalar("StationPointFeature")
        featureData.addMember("obsTime", "Observation time", timeUnit.getUdUnit(), DataType.DOUBLE, obsTime)
        featureData.addMember("nomTime", "Nominal time", timeUnit.getUdUnit(), DataType.DOUBLE, nomTime)
        featureData.addMember("tasmax", "Max temperature", "Celsius", DataType.DOUBLE, tasmax)

        return new SimpleStationPointFeature(dsg, stationFeat, obsTime, nomTime, timeUnit, featureData)
    }

    @IgnoreRest
    def "test2"() {
        setup: "Open test file and flatten the PFCs within"
        FeatureDatasetPoint fdPoint = PointTestUtil.openPointDataset("orthogonal.ncml")
        FlattenedPointCollection flattenedPfc = new FlattenedPointCollection(fdPoint.getPointFeatureCollectionList())

        and: "Create comparator that will sort elements in reverse order of station name."
        Comparator<StationPointFeature> revStationNameComp =
                Ordering.from(SortingPointFeatureCollection.stationNameComparator).reverse();

        and: "sortingPfc sorts simplePfc using revStationNameComp"
        PointFeatureCollection sortingPfc = new SortingPointFeatureCollection(flattenedPfc, revStationNameComp)

        expect: "Sorted list and SortingPointFeatureCollection have same iteration order when using same comparator."
        PointTestUtil.assertIterablesEquals flattenedPfc.asList().toSorted(revStationNameComp), sortingPfc.asList()

        cleanup:
        fdPoint?.close()
    }
}
