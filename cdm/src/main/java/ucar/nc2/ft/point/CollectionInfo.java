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
 *
 */
package ucar.nc2.ft.point;

import ucar.nc2.ft.PointFeature;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.time.CalendarDateUnit;
import ucar.unidata.geoloc.LatLonPoint;
import ucar.unidata.geoloc.LatLonRect;

/**
 * Value class to hold bounds info for a collection
 *
 * @author caron
 * @since 9/25/2015.
 */
public class CollectionInfo {
  public LatLonRect bbox;              // can be null if count == 0
  private CalendarDateRange dateRange;// can be null if count == 0
  public double minTime = Double.MAX_VALUE; // in units of dsg.timeUnit
  public double maxTime = -Double.MAX_VALUE;
  public int nobs;
  public int nfeatures;
  private boolean complete;

  public CollectionInfo() {}

  public CollectionInfo(LatLonRect bbox, CalendarDateRange dateRange, int nfeatures, int nobs) {
    this.bbox = bbox;
    this.dateRange = dateRange;
    this.nfeatures = nfeatures;
    this.nobs = nobs;
  }

  public void extend(PointFeature pointFeat) {
    ++nobs;
    ++nfeatures;

    LatLonPoint pfLoc = pointFeat.getLocation().getLatLon();
    if (bbox == null) {
      bbox = new LatLonRect(pfLoc, pfLoc);
    } else {
      bbox.extend(pfLoc);
    }

    minTime = Math.min(minTime, pointFeat.getObservationTime());
    maxTime = Math.max(maxTime, pointFeat.getObservationTime());
  }

  public void extend(CollectionInfo info) {
    if (info.nobs == 0) return;
    nobs += info.nobs;
    nfeatures++;

    if (bbox == null) bbox = info.bbox;
    else if (info.bbox != null) bbox.extend(info.bbox);

    minTime = Math.min(minTime, info.minTime);
    maxTime = Math.max(maxTime, info.maxTime);
  }

  public CalendarDateRange getCalendarDateRange(CalendarDateUnit timeUnit) {
    if (nobs == 0) return null;
    if (dateRange != null) return dateRange;
    if (timeUnit != null && minTime <= maxTime) {
      dateRange = CalendarDateRange.of(timeUnit.makeCalendarDate(minTime), timeUnit.makeCalendarDate(maxTime));
    }
    return dateRange;
  }

  public void setCalendarDateRange(CalendarDateRange dateRange) {
    this.dateRange = dateRange;
  }

  public boolean isComplete() {
    return complete;
  }

  public void setComplete() {
    if (nobs > 0)
      this.complete = true;
  }

  @Override
  public String toString() {
    return "CollectionInfo{" +
            "bbox=" + bbox +
            ", dateRange=" + getCalendarDateRange(null) +
            ", nfeatures=" + nfeatures +
            ", nobs=" + nobs +
            ", complete=" + complete +
            '}';
  }
}
