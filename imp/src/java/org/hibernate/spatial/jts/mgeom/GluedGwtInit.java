package org.hibernate.spatial.jts.mgeom;

import org.gluewine.gluedgwt.GluewineSerializationPolicy;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequence;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;

/**
 * Initialises the necessary aliases for GluedGwt/HibernateSpatial.
 *
 * @author fks/Frank Gevaerts
 */
public class GluedGwtInit
{
    static
    {
        GluewineSerializationPolicy.addAlias(MCoordinate[].class, Coordinate[].class);
        GluewineSerializationPolicy.addAlias(MCoordinate.class, Coordinate.class);
        GluewineSerializationPolicy.addAlias(MLineString.class, LineString.class);
        GluewineSerializationPolicy.addAlias(MultiMLineString.class, MultiLineString.class);
        GluewineSerializationPolicy.addAlias(MCoordinateSequence.class, CoordinateArraySequence.class);
    }
}
