/*
 * Copyright 1998-2014 University Corporation for Atmospheric Research/Unidata
 *
 *   Portions of this software were developed by the Unidata Program at the
 *   University Corporation for Atmospheric Research.
 *
 *   Access and use of this software shall impose the following obligations
 *   and understandings on the user. The user is granted the right, without
 *   any fee or cost, to use, copy, modify, alter, enhance and distribute
 *   this software, and any derivative works thereof, and its supporting
 *   documentation for any purpose whatsoever, provided that this entire
 *   notice appears in all copies of the software, derivative works and
 *   supporting documentation.  Further, UCAR requests that the user credit
 *   UCAR/Unidata in any publications that result from the use of this
 *   software or in any product that includes this software. The names UCAR
 *   and/or Unidata, however, may not be used in any advertising or publicity
 *   to endorse or promote any products or commercial entity unless specific
 *   written permission is obtained from UCAR/Unidata. The user also
 *   understands that UCAR/Unidata is not obligated to provide the user with
 *   any support, consulting, training or assistance of any kind with regard
 *   to the use, operation and performance of this software nor to provide
 *   the user with any updates, revisions, new versions or "bug fixes."
 *
 *   THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *   IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *   WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *   DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *   INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *   FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *   NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *   WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package ucar.unidata.geoloc;

import com.google.common.base.MoreObjects;

/**
 * A location on the Earth.
 *
 * @author caron
 */
public class EarthLocationImpl implements EarthLocation {
  protected double lat, lon, alt;

  public double getLatitude() { return lat; }

  public double getLongitude() { return lon; }

  public double getAltitude() { return alt; }

  public LatLonPoint getLatLon() {
    return new LatLonPointImpl( lat, lon);
  }

  public boolean isMissing() {
    return Double.isNaN(lat) || Double.isNaN(lon);
  }

  protected EarthLocationImpl() {}

  public EarthLocationImpl( double lat, double lon, double alt) {
    this.lat = lat;
    this.lon = lon;
    this.alt = alt;
  }

  protected void setLatitude(double lat) { this.lat = lat; }
  protected void setLongitude(double lon) { this.lon = lon; }
  protected void setAltitude(double alt) { this.alt = alt; }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
            .add("lat", lat)
            .add("lon", lon)
            .add("alt", alt)
            .toString();
  }
}
