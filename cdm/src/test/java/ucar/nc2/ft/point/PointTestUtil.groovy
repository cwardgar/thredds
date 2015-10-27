package ucar.nc2.ft.point

import com.google.common.math.DoubleMath
import ucar.ma2.Array
import ucar.ma2.MAMath
import ucar.ma2.StructureData
import ucar.ma2.StructureMembers
import ucar.nc2.constants.FeatureType
import ucar.nc2.ft.FeatureDatasetFactoryManager
import ucar.nc2.ft.FeatureDatasetPoint
import ucar.nc2.ft.NoFactoryFoundException
import ucar.nc2.ft.PointFeature
import ucar.nc2.ft.PointFeatureCollection
import ucar.nc2.ft.PointFeatureIterator
import ucar.unidata.geoloc.EarthLocation
import ucar.unidata.geoloc.Station

/**
 * @author cwardgar
 * @since 2015-10-26
 */
class PointTestUtil {
    // Can be used to open datasets in /thredds/cdm/src/test/resources/ucar/nc2/ft/point
    public static FeatureDatasetPoint openPointDataset(String resource)
            throws IOException, NoFactoryFoundException, URISyntaxException {
        File file = new File(PointTestUtil.class.getResource(resource).toURI());
        return (FeatureDatasetPoint) FeatureDatasetFactoryManager.open(FeatureType.ANY_POINT, file.getAbsolutePath(), null, new Formatter());
    }

    public static void writeFeatureCollection(PointFeatureCollection pointFeatColl) throws IOException {
        PointFeatureIterator iter = pointFeatColl.getPointFeatureIterator();
        while (iter.hasNext()) {
            PointFeature pointFeat = iter.next();
            StructureData data = pointFeat.getFeatureData();

            for (StructureMembers.Member member : data.getStructureMembers().getMembers()) {
                System.out.println(member.getName() + "\t\t" + data.getArray(member));
            }

            System.out.println();
        }
    }


    static void assertEquals(PointFeatureCollection featCol1, PointFeatureCollection featCol2) throws IOException {
        if (featCol1.is(featCol2)) {
            return true;
        } else if (featCol1.is(null) || featCol2.is(null)) {
            return false;
        }

        // We must do this comparison first because some PointFeatureCollection implementations, e.g.
        // PointCollectionStreamAbstract, won't have final values for getTimeUnit() and getAltUnits() until
        // getPointFeatureIterator() is called.
        if (!assertEquals(featCol1.getPointFeatureIterator(), featCol2.getPointFeatureIterator())) {
            return false;
        }

        if (!Objects.deepEquals(featCol1.getCollectionFeatureType(), featCol2.getCollectionFeatureType())) {
            return false;
        } else if (!Objects.deepEquals(featCol1.getTimeUnit().getUdUnit(), featCol2.getTimeUnit().getUdUnit())) {
            return false;
        } else if (!Objects.deepEquals(featCol1.getAltUnits(), featCol2.getAltUnits())) {
            return false;
        }

        // We don't care about FeatureCollection.getName(); it's an implementation detail.
        // We're also not going to worry about getExtraVariables(), since that method will likely be moved to
        // FeatureDatasetPoint in NetCDF-Java 5.0.

        return true;
    }

    static void assertEquals(PointFeatureIterator iter1, PointFeatureIterator iter2) throws IOException {
        if (iter1.is(iter2)) {
            return true;
        } else if (iter1.is(null) || iter2.is(null)) {
            return false;
        }

        try {
            while (iter1.hasNext() && iter2.hasNext()) {
                if (!assertEquals(iter1.next(), iter2.next())) {
                    return false;
                }
            }

            return !(iter1.hasNext() || iter2.hasNext());
        } finally {
            iter1.close();
            iter2.close();
        }
    }

    static void assertEquals(StationPointFeature stationPointFeat1, StationPointFeature stationPointFeat2)
            throws IOException {
        if (stationPointFeat1.is(stationPointFeat2)) {
            return true;
        } else if (stationPointFeat1.is(null) || stationPointFeat2.is(null)) {
            return false;
        }

        if (!assertEquals((PointFeature) stationPointFeat1, stationPointFeat2)) {
            return false;
        } else if (!assertEquals(stationPointFeat1.getStation(), stationPointFeat2.getStation())) {
            return false;
        }

        return true;
    }

    static void assertEquals(StationFeature stationFeat1, StationFeature stationFeat2) throws IOException {
        if (stationFeat1.is(stationFeat2)) {
            return true;
        } else if (stationFeat1.is(null) || stationFeat2.is(null)) {
            return false;
        }

        if (!assertEquals((Station) stationFeat1, stationFeat2)) {
            return false;
        } else if (!assertEquals(stationFeat1.getFeatureData(), stationFeat2.getFeatureData())) {
            return false;
        }

        return true;
    }

    static void assertEquals(Station station1, Station station2) {
        if (station1.is(station2)) {
            return true;
        } else if (station1.is(null) || station2.is(null)) {
            return false;
        }

        if (!assertEquals((EarthLocation) station1, station2)) {
            return false;
        } else if (!Objects.deepEquals(station1.getName(), station2.getName())) {
            return false;
        } else if (!Objects.deepEquals(station1.getWmoId(), station2.getWmoId())) {
            return false;
        } else if (!Objects.deepEquals(station1.getDescription(), station2.getDescription())) {
            return false;
        } else if (!Objects.deepEquals(station1.getNobs(), station2.getNobs())) {
            return false;
        }

        return true;
    }

    static void assertEquals(PointFeature pointFeat1, PointFeature pointFeat2) throws IOException {
        if (pointFeat1.is(pointFeat2)) {
            return true;
        } else if (pointFeat1.is(null) || pointFeat2.is(null)) {
            return false;
        }

        if (!assertEquals(pointFeat1.getLocation(), pointFeat2.getLocation())) {
            return false;
        } else if (!DoubleMath.fuzzyEquals(pointFeat1.getObservationTime(), pointFeat2.getObservationTime(), 1.0e-8)) {
            return false;
        } else if (!DoubleMath.fuzzyEquals(pointFeat1.getNominalTime(), pointFeat2.getNominalTime(), 1.0e-8)) {
            return false;
        } else if (!assertEquals(pointFeat1.getFeatureData(), pointFeat2.getFeatureData())) {
            return false;
        }
        // getObservationTimeAsDate() and getObservationTimeAsCalendarDate() derive from getObservationTime().
        // getNominalTimeAsDate() and getNominalTimeAsCalendarDate() derive from getNominalTime().
        // getDataAll() may include data that doesn't "belong" to this feature, so ignore it.
        // getData() is deprecated.

        return true;
    }

    static void assertEquals(EarthLocation loc1, EarthLocation loc2) {
        if (loc1.is(loc2)) {
            return true;
        } else if (loc1.is(null) || loc2.is(null)) {
            return false;
        }

        if (!DoubleMath.fuzzyEquals(loc1.getLatitude(), loc2.getLatitude(), 1.0e-8)) {
            return false;
        } else if (!DoubleMath.fuzzyEquals(loc1.getLongitude(), loc2.getLongitude(), 1.0e-8)) {
            return false;
        } else if (!DoubleMath.fuzzyEquals(loc1.getAltitude(), loc2.getAltitude(), 1.0e-8)) {
            return false;
        } else if (!Objects.deepEquals(loc1.getLatLon(), loc2.getLatLon())) {
            return false;
        } else if (!Objects.deepEquals(loc1.isMissing(), loc2.isMissing())) {
            return false;
        }

        return true;
    }

    static void assertEquals(StructureData sdata1, StructureData sdata2) {
        if (sdata1.is(sdata2)) {
            return true;
        } else if (sdata1.is(null) || sdata2.is(null)) {
            return false;
        }

        if (!assertEquals(sdata1.getStructureMembers(), sdata2.getStructureMembers())) {
            return false;
        }

        for (String memberName : sdata1.getStructureMembers().getMemberNames()) {
            Array memberArray1 = sdata1.getArray(memberName);
            Array memberArray2 = sdata2.getArray(memberName);

            if (!MAMath.fuzzyEquals(memberArray1, memberArray2)) {
                return false;
            }
        }

        return true;
    }

    static void assertEquals(StructureMembers members1, StructureMembers members2) {
        if (members1.is(members2)) {
            return
        }

        assert members1 != null
        assert members2 != null
        assert members1.structureSize == members2.structureSize
        assert members1.name == members2.name
        assertEquals members1.members, members2.members
        // memberHash is derived from shape. No need to include it in computation.
    }

    static void assertEquals(List<StructureMembers.Member> membersList1, List<StructureMembers.Member> membersList2) {
        if (membersList1.is(membersList2)) {
            return
        }

        assert membersList1 != null
        assert membersList2 != null
        assert membersList1.size() == membersList2.size()

        ListIterator<StructureMembers.Member> membersIter1 = membersList1.listIterator();
        ListIterator<StructureMembers.Member> membersIter2 = membersList2.listIterator();

        while (membersIter1.hasNext() && membersIter2.hasNext()) {
            assertEquals(membersIter1.next(), membersIter2.next())
        }
    }

    static void assertEquals(StructureMembers.Member member1, StructureMembers.Member member2) {
        if (member1.is(member2)) {
            return;
        }

        assert member1 != null
        assert member2 != null
        assert member1.name == member2.name
        assert member1.description == member2.description
        assert member1.unitsString == member2.unitsString
        assert member1.dataType == member2.dataType
        assert member1.shape == member2.shape  // Groovy does deep-equals on primitive arrays. TODO: run in debugger.
        assertEquals member1.structureMembers, member2.structureMembers
        assert MAMath.equals(member1.dataArray, member2.dataArray)
        assert member1.dataObject == member2.dataObject
        assert member1.dataParam == member2.dataParam
        // size and isVariableLength are derived from shape. No need to include them in computation.
    }
}
