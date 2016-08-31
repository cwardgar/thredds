package ucar.nc2.ft.point;

import ucar.nc2.constants.FeatureType;
import ucar.nc2.ft.*;
import ucar.nc2.time.CalendarDateUnit;
import ucar.nc2.util.IOIterator;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A PointFeatureCollection formed by aggregating DsgFeatureCollections and flattening their nested structures.
 * This class's {@link #getPointFeatureIterator iterator} returns features in default order, with maximum read
 * efficiency as the goal.
 *
 * @author cwardgar
 * @since 2014/10/08
 */
public class FlattenedPointCollection extends PointCollectionImpl {
    private FeatureType featType;
    private final List<DsgFeatureCollection> dsgFeatCols;

    /**
     * Constructs a PointFeatureCollection by flattening {@code dsgFeatCol}. Collection metadata
     * (i.e. {@link #getTimeUnit() timeUnit}, {@link #getAltUnits() altUnits}, and
     * {@link #getCollectionFeatureType() featType}) are copied from {@code dsgFeatCol}.
     *
     * @param dsgFeatCol  a DsgFeatureCollection
     */
    public FlattenedPointCollection(DsgFeatureCollection dsgFeatCol) {
        this(Collections.singletonList(dsgFeatCol));
    }

    /**
     * Constructs a PointFeatureCollection that is the flattened aggregate of {@code dsgFeatCols}. Collection
     * metadata (i.e. {@link #getTimeUnit() timeUnit}, {@link #getAltUnits() altUnits}, and
     * {@link #getCollectionFeatureType() featType}) are copied from the first collection in the list.
     *
     * @param dsgFeatCols  a list of DsgFeatureCollections
     * @see ucar.nc2.ft.FeatureDatasetPoint#getPointFeatureCollectionList()
     */
    public FlattenedPointCollection(List<DsgFeatureCollection> dsgFeatCols) {
        // The values of timeUnit, altUnits, and featType are temporary until the first DsgFeatureCollection is added.
        super(FlattenedPointCollection.class.getSimpleName(), CalendarDateUnit.unixDateUnit, null);
        this.featType = FeatureType.ANY_POINT;
        this.dsgFeatCols = dsgFeatCols;

        // Replace this.timeUnit, this.altUnits, and this.featType with "typical" values from the first collection.
        // We can't be certain that those values are representative of ALL collections in dsgFeatCols, but it's
        // a decent bet because in practice, the first collection is often the ONLY collection.
        if (!dsgFeatCols.isEmpty()) {
            copyFieldsFrom(dsgFeatCols.get(0));
        }
    }

    private void copyFieldsFrom(DsgFeatureCollection featCol) {
        this.timeUnit = featCol.getTimeUnit();
        this.altUnits = featCol.getAltUnits();
        this.featType = featCol.getCollectionFeatureType();
    }

    @Nonnull
    @Override
    public FeatureType getCollectionFeatureType() throws IllegalStateException {
        return featType;
    }

    @Override
    public PointFeatureIterator getPointFeatureIterator() throws IOException {
        return new FlattenedDatasetPointIterator(dsgFeatCols);
    }


    protected class FlattenedDatasetPointIterator extends PointIteratorAbstract {
        private final Iterator<DsgFeatureCollection> dsgFeatColIter;

        private PointFeatureIterator pfIter;
        private IOIterator<PointFeatureCollection> pfcIter;
        private IOIterator<PointFeatureCC> pfccIter;

        private boolean finished = false;  // set to "true" when close() is called.

        public FlattenedDatasetPointIterator(Iterable<DsgFeatureCollection> dsgFeatColIterable) {
            this.dsgFeatColIter = dsgFeatColIterable.iterator();
            setCalculateBounds(FlattenedPointCollection.this.getInfo());
        }

        @Override
        public boolean hasNext() {
            try {
                // pfIterHasNext() will fail the first time hasNext() is called because no DsgFeatureCollection has
                // been loaded yet.
                while (!pfIterHasNext()) {
                    if (!loadNextDsgFeatureCollection()) {
                        close();  // May not be called otherwise if iter is being used in a for-each.
                        return false;
                    }
                }

                return true;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Attempts to find a PointFeatureIterator in the currently-loaded DsgFeatureCollection that has another
         * available element (i.e. {@code hasNext() == true}). Such an iterator may already be loaded into
         * {@code pfIter}. If not, we'll have to look through {@code pfcIter} and/or {@code pfccIter} to find one.
         * <p>
         * That iterator, if it's found, will be assigned to {@code pfIter} and this method will return {@code true}.
         * Otherwise, it'll return {@code false}, meaning that there are no more unread PointFeatures available in the
         * currently-loaded DsgFeatureCollection.
         *
         * @return  {@code true} if {@code pfIter.hasNext()} will now return {@code true}.
         * @throws IOException  if an I/O error occurs.
         */
        private boolean pfIterHasNext() throws IOException {
            if (pfIter != null) {
                if (pfIter.hasNext()) {
                    return true;
                } else {
                    // We'll need to load a new PointFeatureIterator below. But first, close the old one.
                    pfIter.close();
                }
            }

            while (pfcIterHasNext()) {
                this.pfIter = pfcIter.next().getPointFeatureIterator();
                if (pfIter.hasNext()) {
                    return true;
                }
                // else: Iterator could be empty, in which case we proceed to the next loop iteration.
            }

            return false;
        }

        /**
         * Attempts to find a {@code IOIterator<PointFeatureCollection>} in the currently-loaded DsgFeatureCollection
         * that has another available element (i.e. {@code hasNext() == true}). Such an iterator may already be loaded
         * into {@code pfcIter}. If not, we'll have to look through {@code pfccIter} to find one.
         * <p>
         * The iterator, if it's found, will be assigned to {@code pfcIter} and this method will return {@code true}.
         * Otherwise, it'll return {@code false}, meaning that there are no more unread PointFeatureCollection
         * iterators available in the currently-loaded DsgFeatureCollection
         *
         * @return  {@code true} if {@code pfcIter.hasNext()} will now return {@code true}.
         * @throws IOException  if an I/O error occurs.
         */
        private boolean pfcIterHasNext() throws IOException {
            if (pfcIter != null && pfcIter.hasNext()) {
                return true;
            }

            while (pfccIter != null && pfccIter.hasNext()) {
                pfcIter = pfccIter.next().getCollectionIterator();
                if (pfcIter.hasNext()) {
                    return true;
                }
                // else: Iterator could be empty, in which case we proceed to the next loop iteration.
            }

            return false;
        }

        /**
         * Retrieves the next DsgFeatureCollection from {@code dsgFeatColIter} and assigns it to the appropriate data
         * member. The DsgFeatureCollections returned by {@link FeatureDatasetPoint#getPointFeatureCollectionList} will
         * be one of the following 3 subtypes:
         * <ul>
         *     <li>{@link PointFeatureCollection}: will be assigned to {@code pfIter}</li>
         *     <li>{@link PointFeatureCC}: will be assigned to {@code pfcIter}</li>
         *     <li>{@link PointFeatureCCC}: will be assigned to {@code pfccIter}</li>
         * </ul>
         *
         * @return  {@code true} if the next DsgFeatureCollection was successfully loaded into the appropriate data
         *          member, or {@code false} if no more remain.
         * @throws IOException  if an I/O error occurs.
         */
        private boolean loadNextDsgFeatureCollection() throws IOException {
            if (!dsgFeatColIter.hasNext()) {
                return false;
            }

            // Clear out any iterators belonging to the previous DsgFeatureCollection
            pfIter = null;
            pfcIter = null;
            pfccIter = null;

            DsgFeatureCollection dsgFeatCol = dsgFeatColIter.next();
            if (dsgFeatCol instanceof PointFeatureCollection) {
                pfIter = ((PointFeatureCollection) dsgFeatCol).getPointFeatureIterator();
            } else if (dsgFeatCol instanceof PointFeatureCC) {
                pfcIter = ((PointFeatureCC) dsgFeatCol).getCollectionIterator();
            } else if (dsgFeatCol instanceof PointFeatureCCC) {
                pfccIter = ((PointFeatureCCC) dsgFeatCol).getCollectionIterator();
            } else {
                throw new AssertionError("CAN'T HAPPEN: FeatureDatasetPoint.getPointFeatureCollectionList() " +
                        "only contains PointFeatureCollection, PointFeatureCC, or PointFeatureCCC.");
            }

            return true;
        }

        @Override
        public PointFeature next() {
            if (pfIter == null) {  // Could be null if hasNext() == false or wasn't called at all.
                return null;
            } else {
                PointFeature pointFeat = pfIter.next();
                calcBounds(pointFeat);
                return pointFeat;
            }
        }

        @Override
        public void close() {
            if (finished) {
                return;
            }

            // If hasNext() was repeatedly called until it returned "false", all PointFeatureIterators should've
            // already been closed. However, this may be useful in exceptional circumstances.
            if (pfIter != null) {
                pfIter.close();
            }

            finishCalcBounds();
            finished = true;
        }
    }
}
