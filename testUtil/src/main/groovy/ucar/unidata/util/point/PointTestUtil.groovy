package ucar.unidata.util.point

import org.codehaus.groovy.runtime.IOGroovyMethods
import ucar.ma2.Array
import ucar.ma2.MAMath
import ucar.ma2.StructureData
import ucar.ma2.StructureMembers
import ucar.nc2.constants.FeatureType
import ucar.nc2.ft.*
import ucar.nc2.ft.point.StationFeature
import ucar.nc2.ft.point.StationPointFeature
import ucar.unidata.geoloc.EarthLocation
import ucar.unidata.geoloc.Station

/**
 * @author cwardgar
 * @since 2015-10-26
 */
class PointTestUtil {
    public static FeatureDatasetPoint openClassResourceAsPointDataset(Class<?> clazz, String resource)
            throws IOException, NoFactoryFoundException, URISyntaxException {
        File file = new File(clazz.getResource(resource).toURI());
        return openPointDataset(file.getAbsolutePath());
    }
    
    public static FeatureDatasetPoint openPointDataset(String location) throws IOException, NoFactoryFoundException {
        return (FeatureDatasetPoint) FeatureDatasetFactoryManager.open(
                FeatureType.ANY_POINT, location, null, new Formatter());
    }

    static void assertEquals(PointFeatureCollection featCol1, PointFeatureCollection featCol2) throws IOException {
        if (featCol1.is(featCol2)) {
            return
        }

        assert featCol1 && featCol2

        // We must do this comparison first because some DsgFeatureCollection implementations, e.g.
        // PointCollectionStreamAbstract, won't have final values for getCalendarDateRange(), getBoundingBox(), and
        // size() until after iterating through the collection.
        IOGroovyMethods.withCloseable(featCol1.pointFeatureIterator) { PointFeatureIterator pfIter1 ->
            IOGroovyMethods.withCloseable(featCol2.pointFeatureIterator) { PointFeatureIterator pfIter2 ->
                assertEquals pfIter1, pfIter2
            }
        }

        assertEquals featCol1 as DsgFeatureCollection, featCol2 as DsgFeatureCollection
    }

    static void assertEquals(DsgFeatureCollection dsgFeatCol1, DsgFeatureCollection dsgFeatCol2) {
        if (dsgFeatCol1.is(dsgFeatCol2)) {
            return
        }

        assert dsgFeatCol1 && dsgFeatCol2
        assert dsgFeatCol1.collectionFeatureType == dsgFeatCol2.collectionFeatureType
        assert dsgFeatCol1.timeUnit == dsgFeatCol2.timeUnit
        assert dsgFeatCol1.altUnits == dsgFeatCol2.altUnits
        assert dsgFeatCol1.calendarDateRange == dsgFeatCol2.calendarDateRange
        assert dsgFeatCol1.boundingBox == dsgFeatCol2.boundingBox
        assert dsgFeatCol1.size() == dsgFeatCol2.size()
        // DsgFeatureCollection.getName() will be different depending on the machinery that created the collection,
        // e.g. Table/NestedTable vs PointStreamProto. Ultimately, we don't care; it's just an implementation detail.
    }

    static void assertIterablesEquals(Iterable<PointFeature> iterable1, Iterable<PointFeature> iterable2)
            throws IOException {
        if (iterable1.is(iterable2)) {
            return
        }

        iterable1.each {
            println it
        }
        println "----"
        iterable2.each {
            println it
        }

        assert iterable1 && iterable2
        assertEquals iterable1.iterator(), iterable2.iterator()
    }

    static void assertEquals(Iterator<PointFeature> iter1, Iterator<PointFeature> iter2) throws IOException {
        if (iter1.is(iter2)) {
            return
        }

        assert iter1 && iter2
        while (iter1.hasNext() && iter2.hasNext()) {
            assertEquals iter1.next(), iter2.next()
        }

        // Iterators have the same number of elements.
        assert !iter1.hasNext()
        assert !iter2.hasNext()
    }

    static void assertEquals(StationPointFeature stationPointFeat1, StationPointFeature stationPointFeat2)
            throws IOException {
        if (stationPointFeat1.is(stationPointFeat2)) {
            return
        }

        assert stationPointFeat1 && stationPointFeat2
        assertEquals stationPointFeat1 as PointFeature, stationPointFeat2 as PointFeature
        assertEquals stationPointFeat1.station, stationPointFeat2.station
    }

    static void assertEquals(StationFeature stationFeat1, StationFeature stationFeat2) throws IOException {
        if (stationFeat1.is(stationFeat2)) {
            return
        }

        assert stationFeat1 && stationFeat2
        assertEquals stationFeat1 as Station, stationFeat2 as Station
        assertEquals stationFeat1.featureData, stationFeat2.featureData
    }

    static void assertEquals(Station station1, Station station2) {
        if (station1.is(station2)) {
            return
        }

        assert station1 && station2
        assertEquals station1 as EarthLocation, station2 as EarthLocation
        assert station1.name == station2.name
        assert station1.wmoId == station2.wmoId
        assert station1.description == station2.description

        // "nobs" will always be "-1" if the Station was built by the Table/NestedTable machinery.
        // So, also consider two "nobs" equal if at least one of them is "-1" (unknown).
        assert station1.nobs == station2.nobs || station1.nobs == -1 || station2.nobs == -1
    }

    static void assertEquals(PointFeature pointFeat1, PointFeature pointFeat2) throws IOException {
        if (pointFeat1.is(pointFeat2)) {
            return
        }

        assert pointFeat1 && pointFeat2
        assertEquals pointFeat1.location, pointFeat2.location
        assert pointFeat1.observationTime == pointFeat2.observationTime
        assert pointFeat1.nominalTime == pointFeat2.nominalTime
        assertEquals pointFeat1.featureData, pointFeat2.featureData

        // getObservationTimeAsCalendarDate() derives from getObservationTime().
        // getNominalTimeAsCalendarDate() derives from getNominalTime().
        // getDataAll() may include data that doesn't "belong" to this feature, so ignore it.
        // getFeatureCollection() was examined upstream in assertEquals(PointFeatureCollection, PointFeatureCollection)
    }

    static void assertEquals(EarthLocation loc1, EarthLocation loc2) {
        if (loc1.is(loc2)) {
            return
        }

        assert loc1 && loc2
        assert loc1.latitude == loc2.latitude
        assert loc1.longitude == loc2.longitude
        assert loc1.altitude == loc2.altitude
        assert loc1.latLon == loc2.latLon
        assert loc1.missing == loc2.missing
    }

    static void assertEquals(StructureData sdata1, StructureData sdata2) {
        if (sdata1.is(sdata2)) {
            return
        }

        assert sdata1 && sdata2
        assertEquals sdata1.structureMembers, sdata2.structureMembers

        sdata1.structureMembers.memberNames.each {
            Array memberArray1 = sdata1.getArray(it);
            Array memberArray2 = sdata2.getArray(it);

            assert MAMath.equals(memberArray1, memberArray2)
        }
    }

    static void assertEquals(StructureMembers members1, StructureMembers members2) {
        if (members1.is(members2)) {
            return
        }

        assert members1 && members2
        assert members1.structureSize == members2.structureSize
        assertEquals members1.members, members2.members
        // StructureMembers.getName() will be different depending on the machinery that created the Structure,
        // e.g. Table/NestedTable vs PointStreamProto. Ultimately, we don't care; it's just an implementation detail.
        // Also, StructureMembers.memberHash is derived from StructureMembers.members; no need to test it.
    }

    static void assertEquals(List<StructureMembers.Member> membersList1, List<StructureMembers.Member> membersList2) {
        if (membersList1.is(membersList2)) {
            return
        }

        assert membersList1 && membersList2
        assert membersList1.size() == membersList2.size()

        ListIterator<StructureMembers.Member> membersIter1 = membersList1.listIterator();
        ListIterator<StructureMembers.Member> membersIter2 = membersList2.listIterator();

        while (membersIter1.hasNext() && membersIter2.hasNext()) {
            assertEquals membersIter1.next(), membersIter2.next()
        }
    }

    static void assertEquals(StructureMembers.Member member1, StructureMembers.Member member2) {
        if (member1.is(member2)) {
            return;
        }

        assert member1 && member2
        assert member1.name == member2.name
        assert member1.description == member2.description
        assert member1.unitsString == member2.unitsString
        assert member1.dataType == member2.dataType
        assert member1.shape == member2.shape  // Groovy does deep-equals on primitive arrays. TODO: run in debugger.
        assertEquals member1.structureMembers as StructureMembers, member2.structureMembers as StructureMembers

        // size and isVariableLength are derived from shape. No need to include them in computation.

        // We're not comparing the results of getDataArray(), getDataObject(), or getDataParam() here
        // because they are implementation-specific (see note in StructureMembers.java that those methods really
        // shouldn't be public). Also, comparison of the data arrays is already being done in
        // equals(StructureData, StructureData).
    }
}
