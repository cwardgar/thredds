package ucar.nc2.ft.point

import spock.lang.Specification
import ucar.ma2.DataType
import ucar.ma2.StructureDataScalar
import ucar.nc2.constants.FeatureType
import ucar.nc2.ft.DsgFeatureCollection
import ucar.nc2.time.CalendarDateUnit

/**
 * @author cwardgar
 * @since 2015-11-02
 */
class SortingPointFeatureCollectionSpec extends Specification {
    def "test1"() {
        setup:
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

        Comparator<StationPointFeature> revObsTimeComp = new Comparator<StationPointFeature>() {
            @Override
            public int compare(StationPointFeature left, StationPointFeature right) {
                return -Double.compare(left.getObservationTime(), right.getObservationTime());
            }
        };

        SortingPointFeatureCollection sortingPfc = new SortingPointFeatureCollection(simplePfc, revObsTimeComp)

        expect: "reversed simplePfc equals sorted simplePfc"
        PointTestUtil.assertIterablesEquals simplePfc.asList().reverse(), sortingPfc.asList()
    }

    private static StationPointFeature makeStationPointFeature(DsgFeatureCollection dsg, StationFeature stationFeat,
            CalendarDateUnit timeUnit, double obsTime, double nomTime, double tasmax) {
        StructureDataScalar featureData = new StructureDataScalar("StationPointFeature")
        featureData.addMember("obsTime", "Observation time", timeUnit.getUdUnit(), DataType.DOUBLE, obsTime)
        featureData.addMember("nomTime", "Nominal time", timeUnit.getUdUnit(), DataType.DOUBLE, nomTime)
        featureData.addMember("tasmax", "Max temperature", "Celsius", DataType.DOUBLE, tasmax)

        return new SimpleStationPointFeature(dsg, stationFeat, obsTime, nomTime, timeUnit, featureData)
    }
}
