package ucar.nc2.ft.point;

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
import java.util.Iterator;

/**
 * @author cwardgar
 * @since 2015-10-30
 */
public class SortingPointFeatureCollection implements PointFeatureCollection {
    private final PointFeatureCollection delegate;

    public SortingPointFeatureCollection(PointFeatureCollection delegate) {
        this.delegate = delegate;
    }

    ////////////////////////////////////////////// PointFeatureCollection //////////////////////////////////////////////

    @Override
    public PointFeatureIterator getPointFeatureIterator() throws IOException {
        return null;
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
