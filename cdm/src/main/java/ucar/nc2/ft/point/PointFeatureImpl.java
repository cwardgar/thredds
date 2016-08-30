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
package ucar.nc2.ft.point;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import ucar.nc2.ft.DsgFeatureCollection;
import ucar.nc2.ft.PointFeature;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.time.CalendarDateUnit;
import ucar.unidata.geoloc.EarthLocation;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * Abstract superclass for implementations of PointFeature.
 * Concrete subclass must implement getFeatureData() and getDataAll();
 *
 * @author caron
 * @since Feb 29, 2008
 */
public abstract class PointFeatureImpl implements PointFeature, Comparable<PointFeature> {
  protected DsgFeatureCollection dsg;
  protected EarthLocation location;
  protected double obsTime, nomTime;
  protected CalendarDateUnit timeUnit;

  protected PointFeatureImpl(DsgFeatureCollection dsg, CalendarDateUnit timeUnit) {
    this.dsg = Preconditions.checkNotNull(dsg, "dgs == null");
    this.timeUnit = timeUnit;
  }

  public PointFeatureImpl(DsgFeatureCollection dsg, EarthLocation location, double obsTime, double nomTime, CalendarDateUnit timeUnit) {
    this.dsg = Preconditions.checkNotNull(dsg, "dgs == null");
    this.location = Preconditions.checkNotNull(location, "location == null");
    this.obsTime = obsTime;
    this.nomTime = (nomTime == 0) ? obsTime : nomTime; // LOOK temp kludge until protobuf accepts NaN as defaults
    this.timeUnit = Preconditions.checkNotNull(timeUnit, "timeUnit == null");
  }

  @Nonnull
  @Override
  public DsgFeatureCollection getFeatureCollection() {
    return dsg;
  }

  @Nonnull
  @Override
  public EarthLocation getLocation() { return location; }

  @Override
  public double getNominalTime() { return nomTime; }

  @Override
  public double getObservationTime() { return obsTime; }

  public String getDescription() {
    return location.toString(); // ??
  }

  @Nonnull
  @Override
  public CalendarDate getObservationTimeAsCalendarDate() {
    return timeUnit.makeCalendarDate(getObservationTime());
  }

  @Nonnull
  @Override
  public CalendarDate getNominalTimeAsCalendarDate() {
    return timeUnit.makeCalendarDate(getNominalTime());
  }

  @Override
  public int compareTo(@Nonnull PointFeature other) {
    if (obsTime < other.getObservationTime()) return -1;
    if (obsTime > other.getObservationTime()) return 1;
    return 0;
  }

  @Override
  public String toString() {
    MoreObjects.ToStringHelper toStringHelper = MoreObjects.toStringHelper(this);
    toStringHelper.add("location", getLocation());
    toStringHelper.add("obsTime", getObservationTime());
    toStringHelper.add("nomTime", getNominalTime());
    toStringHelper.add("timeUnit", timeUnit);

    try {
      toStringHelper.add("featureData", String.valueOf(getFeatureData()));
    } catch (IOException e) {
      toStringHelper.add("featureData", "IOException");
    }

    return toStringHelper.toString();
  }
}
