package org.hibernate.spatial.jts.mgeom;

import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.SerializationStreamWriter;

/**
 * Custom field serializer for MCoordinateSequence.
 * @author fks/Frank Gevaerts
 */
@SuppressWarnings({"checkstyle:hideutilityclassconstructor", "checkstyle:typename" })
public final class MCoordinateSequence_CustomFieldSerializer
{
    /**
     * Serializes an instance.
     * @param streamWriter the writer
     * @param instance the instance to serialize
     * @throws SerializationException if serialization fails.
     */
    public static void serialize(SerializationStreamWriter streamWriter,
            MCoordinateSequence instance) throws SerializationException
    {
        streamWriter.writeObject(instance.toCoordinateArray());
    }

}
