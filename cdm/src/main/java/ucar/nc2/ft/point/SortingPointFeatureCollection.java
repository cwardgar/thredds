package ucar.nc2.ft.point;

import com.google.common.base.Preconditions;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.ft.PointFeature;
import ucar.nc2.ft.PointFeatureCollection;
import ucar.nc2.ft.PointFeatureIterator;
import ucar.nc2.time.CalendarDateUnit;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

/**
 * @author cwardgar
 * @since 2015-10-30
 */
public class SortingPointFeatureCollection extends PointCollectionImpl {
    //////////////////////////////////////////////////// Static ////////////////////////////////////////////////////

    public static final Comparator<PointFeature> stationNameComparator = (pf1, pf2) -> {
        if (!(pf1 instanceof StationPointFeature && pf2 instanceof StationPointFeature)) {
            return 0;
        } else {
            StationPointFeature spf1 = (StationPointFeature) pf1;
            StationPointFeature spf2 = (StationPointFeature) pf2;
            return spf1.getStation().getName().compareTo(spf2.getStation().getName());
        }
    };

    public static final Comparator<PointFeature> observationTimeComparator =
            (pf1, pf2) -> Double.compare(pf1.getObservationTime(), pf2.getObservationTime());

    /////////////////////////////////////////////////// Instance ///////////////////////////////////////////////////

    private final Comparator<PointFeature> comp;

    /*
     Using a SortedMap of Lists instead of a SortedSet here has 2 benefits:
     1. Elements that compare "equal" will be retained. We don't want to discard a StationPointFeature just
        because it has the same station name as another StationPointFeature, for example.
     2. The relative order of "equal" elements will be maintained. For example, if spf1 and spf2 are "equal" and
        spf1 appears before spf2 in the delagate collection, than spf1 will still come before spf2 when we
        iterate over this SortingPointFeatureCollection.
     */
    private SortedMap<PointFeature, List<PointFeature>> inMemCache;

    private FeatureType featType;

    public SortingPointFeatureCollection() {
        this(stationNameComparator.thenComparing(observationTimeComparator));  // Default.
    }

    public SortingPointFeatureCollection(Comparator<PointFeature> comp) {
        super("tempName", CalendarDateUnit.unixDateUnit, null);  // name and timeUnit are temporary
        this.comp = Preconditions.checkNotNull(comp, "comp == null");
        this.inMemCache = new TreeMap<>(comp);
    }

    public void add(PointFeature pointFeat) throws IOException {
        if (inMemCache.size() == 0) {
            // Copy metadata from first feature's collection
            this.name     = pointFeat.getFeatureCollection().getName();
            this.timeUnit = pointFeat.getFeatureCollection().getTimeUnit();
            this.altUnits = pointFeat.getFeatureCollection().getAltUnits();
            this.featType = pointFeat.getFeatureCollection().getCollectionFeatureType();
        } else {
            // Assert that metadata for features are consistent.
            if (!Objects.equals(this.timeUnit, pointFeat.getFeatureCollection().getTimeUnit())) {
                throw new IllegalArgumentException(String.format(
                        "Incorrect timeUnit for %s. All features must have '%s'.", pointFeat, this.timeUnit));
            }
            if (!Objects.equals(this.altUnits, pointFeat.getFeatureCollection().getAltUnits())) {
                throw new IllegalArgumentException(String.format(
                        "Incorrect altUnits for %s. All features must have '%s'.", pointFeat, this.altUnits));
            }
            if (!Objects.equals(this.featType, pointFeat.getFeatureCollection().getCollectionFeatureType())) {
                throw new IllegalArgumentException(String.format(
                        "Incorrect featType for %s. All features must have '%s'.", pointFeat, this.featType));
            }
        }

        List<PointFeature> bucket = inMemCache.get(pointFeat);
        if (bucket == null) {
            bucket = new LinkedList<>();
            inMemCache.put(pointFeat, bucket);
        }

        bucket.add(pointFeat);
        getInfo().extend(pointFeat);

        // TODO: Have we added too many features? Need to dump them to disk.
    }

    public void addAll(PointFeatureCollection pfc) throws IOException {
        for (PointFeature pointFeat : pfc) {
            add(pointFeat);
        }
    }

    // TODO: When last PointFeature is added and we're no longer in "add" mode, do getInfo().setComplete().

    private class Iter implements java.util.Iterator<PointFeature> {
        private Iterator<List<PointFeature>> bucketsIter;
        private Iterator<PointFeature> featsIter;

        private Iter() {
            bucketsIter = inMemCache.values().iterator();
        }

        @Override
        public boolean hasNext() {  // Method is idempotent.  TODO: Test idempotency.
            while (featsIter == null || !featsIter.hasNext()) {
                if (!bucketsIter.hasNext()) {
                    return false;
                } else {
                    featsIter = bucketsIter.next().iterator();
                }
            }

            assert featsIter != null && featsIter.hasNext();
            return true;
        }

        @Override
        public PointFeature next() {
            if (!hasNext()) {  // Don't rely on user to call this.
                throw new NoSuchElementException("There are no more elements.");
            } else {
                return featsIter.next();
            }
        }
    }

    //////////////////////////////////// PointCollectionImpl ////////////////////////////////////

    @Nonnull
    @Override
    public FeatureType getCollectionFeatureType() throws IllegalStateException {
        return featType;
    }

    // Inheriting subset() from super class. Can I do better? Should I create a brand new SortingPointFeatureCollection
    // and simply add all the features from *this* that satisfy the filter? It gets tricky when features have been
    // serialized. Also, it means we must iterate over *this*.

    //////////////////////////////////// PointFeatureCollection ////////////////////////////////////

    // TODO: Once this method is called, prohibit any further additions to collection.
    @Override
    public PointFeatureIterator getPointFeatureIterator() throws IOException {
        return new PointIteratorAdapter(new Iter());
    }
}
