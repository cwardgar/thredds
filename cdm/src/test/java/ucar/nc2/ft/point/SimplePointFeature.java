package ucar.nc2.ft.point;

import ucar.ma2.StructureData;
import ucar.nc2.ft.DsgFeatureCollection;
import ucar.unidata.geoloc.EarthLocation;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * @author cwardgar
 * @since 2014/08/29
 */
public class SimplePointFeature extends PointFeatureImpl {
    private final StructureData featureData;

    public SimplePointFeature(DsgFeatureCollection dsg, EarthLocation location, double obsTime, double nomTime,
            StructureData featureData) {
        super(dsg, location, obsTime, nomTime);
        this.featureData = featureData;
    }

    @Nonnull
    @Override
    public StructureData getFeatureData() throws IOException {
        return featureData;
    }

    @Nonnull
    @Override
    public StructureData getDataAll() throws IOException {
        return featureData;
    }
}
