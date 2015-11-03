package ucar.nc2.ft.point;

import com.google.common.base.Preconditions;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.ft.PointFeature;
import ucar.nc2.ft.PointFeatureCollection;
import ucar.nc2.ft.PointFeatureIterator;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.time.CalendarDateUnit;
import ucar.unidata.geoloc.LatLonRect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * @author cwardgar
 * @since 2015-10-30
 */
public class SortingPointFeatureCollection implements PointFeatureCollection {
    public static final Comparator<PointFeature> stationNameComparator = (pf1, pf2) -> {
        if (!(pf1 instanceof StationPointFeature && pf2 instanceof StationPointFeature)) {
            return 0;
        } else {
            StationPointFeature spf1 = (StationPointFeature) pf1;
            StationPointFeature spf2 = (StationPointFeature) pf2;
            return spf1.getStation().getName().compareTo(spf2.getStation().getName());
        }
    };

    private final PointFeatureCollection delegate;
    private final Comparator<PointFeature> comp;

    public SortingPointFeatureCollection(PointFeatureCollection delegate) {
        this(delegate, stationNameComparator);
    }

    public SortingPointFeatureCollection(PointFeatureCollection delegate, Comparator<PointFeature> comp) {
        this.delegate = Preconditions.checkNotNull(delegate, "delegate == null");
        this.comp = Preconditions.checkNotNull(comp, "comp == null");
    }

    private class Iter implements java.util.Iterator<PointFeature> {
        /*
         Using a SortedMap of Lists instead of a SortedSet here has 2 benefits:
         1. Elements that compare "equal" will be retained. We don't want to discard a StationPointFeature just
            because it has the same station name as another StationPointFeature, for example.
         2. The relative order of "equal" elements will be maintained. For example, if spf1 and spf2 are "equal" and
            spf1 appears before spf2 in the delagate collection, than spf1 will still come before spf2 when we
            iterate over this SortingPointFeatureCollection.
         */
        private SortedMap<PointFeature, List<PointFeature>> inMemCache;

        private StationFeatureCopyFactory stationCopyFactory;
        private PointFeatureCopyFactory pointCopyFactory;

        private Iterator<List<PointFeature>> bucketsIter;
        private Iterator<PointFeature> featsIter;

        private void init() throws IOException {
            inMemCache = new TreeMap<>(comp);
            addAllToCache(delegate);
            bucketsIter = inMemCache.values().iterator();
        }

        private void addAllToCache(PointFeatureCollection pfc) throws IOException {
            try (PointFeatureIterator pfIter = pfc.getPointFeatureIterator()) {
                while (pfIter.hasNext()) {
                    addToCache(pfIter.next());
                }
            }
        }

        private void addToCache(PointFeature pf) throws IOException {
            PointFeature pfCopy;

            if (pf instanceof StationPointFeature) {
                StationPointFeature spf = (StationPointFeature) pf;
                pfCopy = getStationCopyFactory(spf).deepCopy(spf);
            } else {
                pfCopy = getPointCopyFactory(pf).deepCopy(pf);
            }

            List<PointFeature> bucket = inMemCache.get(pfCopy);
            if (bucket == null) {
                bucket = new LinkedList<>();
                inMemCache.put(pfCopy, bucket);
            }

            bucket.add(pfCopy);
        }

        private StationFeatureCopyFactory getStationCopyFactory(StationPointFeature spf) throws IOException {
            if (stationCopyFactory == null) {
                stationCopyFactory = new StationFeatureCopyFactory(spf);
            }
            return stationCopyFactory;
        }

        private PointFeatureCopyFactory getPointCopyFactory(PointFeature pf) throws IOException {
            if (pointCopyFactory == null) {
                pointCopyFactory = new PointFeatureCopyFactory(pf);
            }
            return pointCopyFactory;
        }

        @Override
        public boolean hasNext() {  // Method is idempotent.
            if (inMemCache == null) {
                try {
                    init();  // Initializes inMemCache and bucketsIter.
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

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

    ////////////////////////////////////////////// PointFeatureCollection //////////////////////////////////////////////

    @Override
    public PointFeatureIterator getPointFeatureIterator() throws IOException {
        return new PointIteratorAdapter(new Iter());
    }

    @Nullable
    @Override
    public PointFeatureCollection subset(LatLonRect boundingBox, CalendarDateRange dateRange) throws IOException {
        return new SortingPointFeatureCollection(delegate.subset(boundingBox, dateRange));
    }

    ////////////// These 4 methods are all copied from PointCollectionImpl //////////////

    protected PointFeatureIterator localIterator;

    @Override
    public boolean hasNext() throws IOException {
        if (localIterator == null) resetIteration();
        return localIterator.hasNext();
    }

    @Override
    public void finish() {
        if (localIterator != null)
            localIterator.close();
    }

    @Override
    public PointFeature next() throws IOException {
        return localIterator.next();
    }

    @Override
    public void resetIteration() throws IOException {
        localIterator = getPointFeatureIterator();
    }

    /////////////////////////////////////////////// DsgFeatureCollection ///////////////////////////////////////////////

    @Nonnull
    @Override
    public String getName() {
        return delegate.getName();
    }

    @Nonnull
    @Override
    public FeatureType getCollectionFeatureType() {
        return delegate.getCollectionFeatureType();
    }

    @Nonnull
    @Override
    public CalendarDateUnit getTimeUnit() {
        return delegate.getTimeUnit();
    }

    @Nullable
    @Override
    public String getAltUnits() {
        return delegate.getAltUnits();
    }

    @Nullable
    @Override
    public CalendarDateRange getCalendarDateRange() {
        return delegate.getCalendarDateRange();
    }

    @Nullable
    @Override
    public LatLonRect getBoundingBox() {
        return delegate.getBoundingBox();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    ///////////////////////////////////////////////////// Iterable /////////////////////////////////////////////////////

    // Copied from PointCollectionImpl.
    @Override
    public Iterator<PointFeature> iterator() {
        try {
            return getPointFeatureIterator();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
