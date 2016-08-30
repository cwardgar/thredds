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

package ucar.nc2.ft.point.remote;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import ucar.ma2.*;
import ucar.nc2.ft.DsgFeatureCollection;
import ucar.nc2.ft.PointFeature;
import ucar.nc2.ft.PointFeatureCollection;
import ucar.nc2.ft.PointFeatureIterator;
import ucar.nc2.ft.point.PointFeatureImpl;
import ucar.nc2.stream.NcStream;
import ucar.nc2.stream.NcStreamProto;
import ucar.nc2.time.CalendarDateUnit;
import ucar.unidata.geoloc.EarthLocation;
import ucar.unidata.geoloc.EarthLocationImpl;
import ucar.unidata.geoloc.Station;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Defines the point stream format, along with pointStream.proto.
 *
 cd c:/dev/github/thredds/cdm/src/main/java
 protoc --proto_path=. --java_out=. ucar/nc2/ft/point/remote/pointStream.proto
 *
 * @author caron
 * @since Feb 16, 2009
 */
public class PointStream {
  public enum MessageType {
    Start, Header, Data, End, Error, Eos,
    StationList, PointFeatureCollection, PointFeature
  }

  static private final byte[] MAGIC_StationList = new byte[]{(byte) 0xfe, (byte) 0xfe, (byte) 0xef, (byte) 0xef};
  static private final byte[] MAGIC_PointFeatureCollection = new byte[]{(byte) 0xfa, (byte) 0xfa, (byte) 0xaf, (byte) 0xaf};
  static private final byte[] MAGIC_PointFeature = new byte[]{(byte) 0xf0, (byte) 0xf0, (byte) 0x0f, (byte) 0x0f};

  static private final boolean debug = false;

  static public MessageType readMagic(InputStream is) throws IOException {
    byte[] b = new byte[4];
    int done = NcStream.readFully(is, b);
    if (done != 4) return MessageType.Eos;

    if (test(b, MAGIC_PointFeature)) return MessageType.PointFeature;
    if (test(b, MAGIC_PointFeatureCollection)) return MessageType.PointFeatureCollection;
    if (test(b, MAGIC_StationList)) return MessageType.StationList;
    if (test(b, NcStream.MAGIC_START)) return MessageType.Start;
    if (test(b, NcStream.MAGIC_HEADER)) return MessageType.Header;
    if (test(b, NcStream.MAGIC_DATA)) return MessageType.Data;
    if (test(b, NcStream.MAGIC_END)) return MessageType.End;
    if (test(b, NcStream.MAGIC_ERR)) return MessageType.Error;
    return null;
  }

  static public int writeMagic(OutputStream out, MessageType type) throws IOException {
    switch (type) {
      case PointFeature:
        return NcStream.writeBytes(out, PointStream.MAGIC_PointFeature);
      case PointFeatureCollection:
        return NcStream.writeBytes(out, PointStream.MAGIC_PointFeatureCollection);
      case StationList:
        return NcStream.writeBytes(out, PointStream.MAGIC_StationList);
      case Start:
        return NcStream.writeBytes(out, NcStream.MAGIC_START);
      case End:
         return NcStream.writeBytes(out, NcStream.MAGIC_END);
      case Error:
        return NcStream.writeBytes(out, NcStream.MAGIC_ERR);
    }
    return 0;
  }

  private static boolean test(byte[] b, byte[] m) {
    if (b.length != m.length) return false;
    for (int i = 0; i < b.length; i++)
      if (b[i] != m[i]) return false;
    return true;
  }

  static public PointStreamProto.PointFeatureCollection encodePointFeatureCollection(
          String name, String timeUnitString, String altUnits, PointFeature pf) throws IOException {
    PointStreamProto.PointFeatureCollection.Builder builder = PointStreamProto.PointFeatureCollection.newBuilder();
    if (name == null)
      System.out.printf("HEY null pointstream name%n");
    builder.setName(name);
    builder.setTimeUnit(timeUnitString);

    if (altUnits != null) {
      builder.setAltUnit(altUnits);
    }

    StructureData sdata = pf.getDataAll();
    StructureMembers sm = sdata.getStructureMembers();
    for (StructureMembers.Member m : sm.getMembers()) {
      PointStreamProto.Member.Builder mbuilder = PointStreamProto.Member.newBuilder();
      mbuilder.setName(m.getName());
      if (null != m.getDescription())
        mbuilder.setDesc(m.getDescription());
      if (null != m.getUnitsString())
        mbuilder.setUnits(m.getUnitsString());
      mbuilder.setDataType(NcStream.convertDataType(m.getDataType()));
      mbuilder.setSection(NcStream.encodeSection(new ucar.ma2.Section(m.getShape())));
      builder.addMembers(mbuilder);
    }

    return builder.build();
  }

  static public PointStreamProto.PointFeature encodePointFeature(PointFeature pf) throws IOException {
    PointStreamProto.Location.Builder locBuilder = PointStreamProto.Location.newBuilder();
    locBuilder.setTime(pf.getObservationTime());
    locBuilder.setNomTime(pf.getNominalTime());

    EarthLocation loc = pf.getLocation();
    locBuilder.setLat(loc.getLatitude());
    locBuilder.setLon(loc.getLongitude());
    locBuilder.setAlt(loc.getAltitude());

    PointStreamProto.PointFeature.Builder builder = PointStreamProto.PointFeature.newBuilder();
    builder.setLoc(locBuilder);

    StructureData sdata = pf.getDataAll();
    ArrayStructureBB abb = StructureDataDeep.copyToArrayBB(sdata);
    ByteBuffer bb = abb.getByteBuffer();
    if (debug) {
      StructureMembers sm = sdata.getStructureMembers();
      int size = sm.getStructureSize();
      System.out.printf("encodePointFeature size= %d bb=%d%n", size, bb.position());
    }
    builder.setData(ByteString.copyFrom(bb.array()));
    List<Object> heap = abb.getHeap();
    if (heap != null) {
      for (Object ho : heap) {
        if (ho instanceof String)
          builder.addSdata((String) ho);
        else if (ho instanceof String[])
          builder.addAllSdata(Arrays.asList((String[]) ho));
        else
          throw new IllegalStateException("illegal object on heap = "+ho);
      }
    }
    return builder.build();
  }

  static public PointStreamProto.StationList encodeStations(List<Station> stnList) throws IOException {
    PointStreamProto.StationList.Builder stnBuilder = PointStreamProto.StationList.newBuilder();
    for (Station loc : stnList) {
      PointStreamProto.Station.Builder locBuilder = PointStreamProto.Station.newBuilder();

      locBuilder.setId(loc.getName());
      locBuilder.setLat(loc.getLatitude());
      locBuilder.setLon(loc.getLongitude());
      locBuilder.setAlt(loc.getAltitude());
      if (loc.getDescription() != null)
        locBuilder.setDesc(loc.getDescription());
      if (loc.getWmoId() != null)
        locBuilder.setWmoId(loc.getWmoId());

      stnBuilder.addStations(locBuilder);
    }

    return stnBuilder.build();
  }

  //////////////////////////////////////////////////////////////////
  // decoding
  // makes a PointFeature from the raw bytes of the protobuf message

  static class ProtobufPointFeatureMaker implements FeatureMaker {
    private CalendarDateUnit dateUnit;
    private StructureMembers sm;

    ProtobufPointFeatureMaker(PointStreamProto.PointFeatureCollection pfc) throws IOException {
      try {
        // LOOK No calendar
        dateUnit = CalendarDateUnit.of(null, pfc.getTimeUnit());
      } catch (Exception e) {
        e.printStackTrace();
        dateUnit = CalendarDateUnit.unixDateUnit;
      }

      sm = new StructureMembers(pfc.getName());
      for (PointStreamProto.Member m : pfc.getMembersList()) {
        String name = m.getName();
        String desc = m.getDesc().length() > 0 ? m.getDesc() : null;
        String units = m.getUnits().length() > 0 ? m.getUnits() : null;
        DataType dtype = NcStream.convertDataType(m.getDataType());
        int[] shape = NcStream.decodeSection(m.getSection()).getShape();

        sm.addMember(name, desc, units, dtype, shape);
      }
      ArrayStructureBB.setOffsets(sm);
    }

    @Override
    public PointFeature make(DsgFeatureCollection dsg, byte[] rawBytes) throws InvalidProtocolBufferException {
      PointStreamProto.PointFeature pfp = PointStreamProto.PointFeature.parseFrom(rawBytes);
      PointStreamProto.Location locp = pfp.getLoc();
      EarthLocationImpl location = new EarthLocationImpl(locp.getLat(), locp.getLon(), locp.getAlt());
      return new MyPointFeature(dsg, location, locp.getTime(), locp.getNomTime(), pfp);
    }

    private class MyPointFeature extends PointFeatureImpl {
      PointStreamProto.PointFeature pfp;

      MyPointFeature(DsgFeatureCollection dsg, EarthLocation location, double obsTime, double nomTime,
              PointStreamProto.PointFeature pfp) {
        super(dsg, location, obsTime, nomTime);
        this.pfp = pfp;
      }

      @Nonnull
      @Override
      public StructureData getFeatureData() throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(pfp.getData().toByteArray());
        ArrayStructureBB asbb = new ArrayStructureBB(sm, new int[]{1}, bb, 0);
        for (String s : pfp.getSdataList()) {
          asbb.addObjectToHeap(s);
        }
        return asbb.getStructureData(0);
      }

      @Nonnull
      @Override
      public StructureData getDataAll() throws IOException {
        return getFeatureData();
      }

      public String toString() {
        return location + " obs=" + obsTime + " nom=" + nomTime;
      }
    }
  }

  // Adapted from thredds.server.cdmremote.PointWriter.WriterNcstream
  public static int write(PointFeatureCollection pointFeatCol, File outFile) throws IOException {
    try (PointFeatureIterator pointFeatIter = pointFeatCol.getPointFeatureIterator();
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
      int numWritten = 0;

      while (pointFeatIter.hasNext()) {
        try {
          PointFeature pointFeat = pointFeatIter.next();

          if (numWritten == 0) {
            PointStreamProto.PointFeatureCollection protoPfc = PointStream.encodePointFeatureCollection(
                    outFile.getCanonicalPath(),
                    pointFeatCol.getTimeUnit().getUdUnit(),
                    pointFeatCol.getAltUnits(),
                    pointFeat);
            byte[] data = protoPfc.toByteArray();

            PointStream.writeMagic(out, MessageType.PointFeatureCollection);
            NcStream.writeVInt(out, data.length);
            out.write(data);
          }

          PointStreamProto.PointFeature protoPointFeat = PointStream.encodePointFeature(pointFeat);
          byte[] data = protoPointFeat.toByteArray();

          PointStream.writeMagic(out, MessageType.PointFeature);
          NcStream.writeVInt(out, data.length);
          out.write(data);

          ++numWritten;
        } catch (Throwable t) {
          NcStreamProto.Error protoError =
                  NcStream.encodeErrorMessage(t.getMessage() != null ? t.getMessage() : t.getClass().getName());
          byte[] data = protoError.toByteArray();

          PointStream.writeMagic(out, PointStream.MessageType.Error);
          NcStream.writeVInt(out, data.length);
          out.write(data);

          throw new IOException(t);
        }
      }

      PointStream.writeMagic(out, PointStream.MessageType.End);
      return numWritten;
    }
  }
}
