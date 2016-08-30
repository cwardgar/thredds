/*
 * Copyright 1998-2015 John Caron and University Corporation for Atmospheric Research/Unidata
 *
 *  Portions of this software were developed by the Unidata Program at the
 *  University Corporation for Atmospheric Research.
 *
 *  Access and use of this software shall impose the following obligations
 *  and understandings on the user. The user is granted the right, without
 *  any fee or cost, to use, copy, modify, alter, enhance and distribute
 *  this software, and any derivative works thereof, and its supporting
 *  documentation for any purpose whatsoever, provided that this entire
 *  notice appears in all copies of the software, derivative works and
 *  supporting documentation.  Further, UCAR requests that the user credit
 *  UCAR/Unidata in any publications that result from the use of this
 *  software or in any product that includes this software. The names UCAR
 *  and/or Unidata, however, may not be used in any advertising or publicity
 *  to endorse or promote any products or commercial entity unless specific
 *  written permission is obtained from UCAR/Unidata. The user also
 *  understands that UCAR/Unidata is not obligated to provide the user with
 *  any support, consulting, training or assistance of any kind with regard
 *  to the use, operation and performance of this software nor to provide
 *  the user with any updates, revisions, new versions or "bug fixes."
 *
 *  THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *  INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *  FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *  NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *  WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package ucar.nc2.ft.point.standard;

import ucar.ma2.StructureData;
import ucar.ma2.StructureDataIterator;
import ucar.nc2.ft.PointFeatureIterator;
import ucar.nc2.ft.StationTimeSeriesFeature;
import ucar.nc2.ft.point.StationFeature;
import ucar.nc2.ft.point.StationHelper;
import ucar.nc2.ft.point.StationTimeSeriesCollectionImpl;
import ucar.nc2.ft.point.StationTimeSeriesFeatureImpl;
import ucar.nc2.time.CalendarDateUnit;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * Object Heirarchy for StationFeatureCollection:
 * StationFeatureCollection (StandardStationCollectionImpl
 * PointFeatureCollectionIterator (anon)
 * StationFeature (StandardStationFeatureImpl)
 * PointFeatureIterator (StandardStationPointIterator)
 * PointFeatureImpl
 *
 * @author caron
 * @since Mar 28, 2008
 */
public class StandardStationCollectionImpl extends StationTimeSeriesCollectionImpl {
  private NestedTable ft;

  StandardStationCollectionImpl(NestedTable ft, CalendarDateUnit timeUnit, String altUnits) throws IOException {
    super(ft.getName(), timeUnit, altUnits);
    this.ft = ft;
    this.extras = ft.getExtras();
  }

  /**
   * Make a Station from the station data structure.
   *
   * @param stationData station data structure
   * @param recnum      station data recnum within table
   * @return Station or null, skip this Station
   */
  public StationTimeSeriesFeature makeStation(StructureData stationData, int recnum) {
    StationFeature s = ft.makeStation(stationData);
    if (s == null) return null;
    return new StandardStationFeatureImpl(s, timeUnit, stationData, recnum);
  }

  @Override
  protected StationHelper createStationHelper() throws IOException {
    StationHelper stationHelper = new StationHelper();

    try (StructureDataIterator siter = ft.getStationDataIterator()) {
      while (siter.hasNext()) {
        StructureData stationData = siter.next();
        StationTimeSeriesFeature stfs = makeStation(stationData, siter.getCurrentRecno());
        if (stfs != null) {
          stationHelper.addStation(stfs);
        }
      }
    }

    return stationHelper;
  }

  private class StandardStationFeatureImpl extends StationTimeSeriesFeatureImpl {
    int recnum;
    StructureData stationData;

    StandardStationFeatureImpl(StationFeature s, CalendarDateUnit dateUnit, StructureData stationData, int recnum) {
      super(s, dateUnit, StandardStationCollectionImpl.this.getAltUnits(), -1);
      this.recnum = recnum;
      this.stationData = stationData;
    }

    // an iterator over the observations for this station

    @Override
    public PointFeatureIterator getPointFeatureIterator() throws IOException {
      Cursor cursor = new Cursor(ft.getNumberOfLevels());
      cursor.recnum[1] = recnum;
      cursor.tableData[1] = stationData;
      cursor.currentIndex = 1;
      ft.addParentJoin(cursor); // there may be parent joins

      StructureDataIterator obsIter = ft.getLeafFeatureDataIterator(cursor);
      return new StandardPointFeatureIterator(this, ft, obsIter, cursor);
    }

    @Nonnull
    @Override
    public StructureData getFeatureData() {
      return stationData;
    }

  }
}
