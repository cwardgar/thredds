package ucar.nc2.ft.point;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(SortingPointFeatureCollection.class);

    /** The possible states that this collection may be in. They are mutually exclusive. */
    public enum State {
        /**
         * In this state, new {@link PointFeature}s may be added, via {@link #add} and {@link #addAll}.
         * Once {@link #getPointFeatureIterator()} is called, the active state becomes {@link #RETRIEVAL}.
         */
        ADDITION,
        /**
         * In this state, previously-added {@link PointFeature}s may be retrieved in sorted order via
         * {@link #getPointFeatureIterator()}. No further additions are permitted.
         */
        RETRIEVAL
    }

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

    /*
     Using a SortedMap of Lists instead of a SortedSet here has 2 benefits:
     1. Elements that compare "equal" will be retained. We don't want to discard a StationPointFeature just
        because it has the same station name as another StationPointFeature, for example.
     2. The relative order of "equal" elements will be maintained. For example, if spf1 and spf2 are "equal" and
        spf1 is added before spf2, than spf1 will still come before spf2 when we retrieve the features later.
     */
    private final SortedMap<PointFeature, List<PointFeature>> inMemCache;

    private State state;
    private FeatureType featType;

    public SortingPointFeatureCollection() {
        this(stationNameComparator.thenComparing(observationTimeComparator));  // Default.
    }

    public SortingPointFeatureCollection(Comparator<PointFeature> comp) {
        // The values of name, timeUnit, altUnits, and featType are temporary until the first feature is added.
        super("tempName", CalendarDateUnit.unixDateUnit, null);
        this.inMemCache = new TreeMap<>(comp);
        this.state = State.ADDITION;
        this.featType = FeatureType.ANY_POINT;  // "UNKNOWN_POINT" would be more appropriate.

        // Create the CollectionInfo object. Causes getSize() and getNobs() to return 0 before a feature is added
        // instead of -1 (unknown).
        getInfo();
    }

    public void add(PointFeature pointFeat) throws IllegalStateException, IOException {
        if (state != State.ADDITION) {
            throw new IllegalStateException("No more features can be added once getPointFeatureIterator() is called.");
        }

        if (inMemCache.isEmpty()) {
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
        info.extend(pointFeat);

        // TODO: Have we added too many features? Need to dump them to disk.
    }

    public void addAll(PointFeatureCollection pfc) throws IllegalStateException, IOException {
        for (PointFeature pointFeat : pfc) {
            add(pointFeat);
        }
    }

    private class Iter implements java.util.Iterator<PointFeature> {
        private Iterator<List<PointFeature>> bucketsIter;
        private Iterator<PointFeature> featsIter;

        private Iter() {
            bucketsIter = inMemCache.values().iterator();
        }

        @Override
        public boolean hasNext() {  // Method is idempotent.
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
            // This iterator will be wrapped in a PointIteratorAdapter when returned to the user.
            // Its next() method ensures that a NoSuchElementException is thrown if there are no more elements.
            // There's no need to do yet another hasNext() check here.
            return featsIter.next();
        }
    }

    //////////////////////////////////// PointCollectionImpl ////////////////////////////////////

    @Nonnull
    @Override
    public FeatureType getCollectionFeatureType() throws IllegalStateException {
        return featType;
    }

    //////////////////////////////////// PointFeatureCollection ////////////////////////////////////

    @Override
    public PointFeatureIterator getPointFeatureIterator() throws IOException {
        if (state != State.RETRIEVAL) {
            logger.debug("Switching collection's state from {} to {}.", state, State.RETRIEVAL);
            this.state = State.RETRIEVAL;
            info.setComplete();  // Collection metadata is complete because we've finished adding features.
        }

        return new PointIteratorAdapter(new Iter());
    }
}
