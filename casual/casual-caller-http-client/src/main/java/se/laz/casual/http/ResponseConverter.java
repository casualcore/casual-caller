package se.laz.casual.http;

import jakarta.ws.rs.core.Response;
import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.type.CStringBuffer;
import se.laz.casual.api.buffer.type.JsonBuffer;
import se.laz.casual.api.buffer.type.OctetBuffer;
import se.laz.casual.api.buffer.type.fielded.FieldedTypeBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ResponseConverter
{
    private ResponseConverter()
    {}

    public static Optional<CasualBuffer> convert(Response response)
    {
        Objects.requireNonNull(response, "response cannot be null");
        String type = response.getHeaderString("Content-Type");
        if(null == type)
        {
            return Optional.empty();
        }
        CasualContentType contentType = CasualContentType.unmarshall(type);
        return switch (contentType)
        {
            case OCTET,X_OCTET -> Optional.of(createOctetBuffer(response.readEntity(byte[].class)));
            case JSON -> Optional.of(createJsonBuffer(response.readEntity(byte[].class)));
            case FIELD -> Optional.of(createFieldedBuffer(response.readEntity(byte[].class)));
            case STRING -> Optional.of(createCStringBuffer(response.readEntity(byte[].class)));
            case NULL -> Optional.empty();
        };
    }

    private static CasualBuffer createCStringBuffer(byte[] data)
    {
        List<byte[]> bytes = new ArrayList<>();
        bytes.add(data);
        return CStringBuffer.of(bytes);
    }

    private static CasualBuffer createFieldedBuffer(byte[] data)
    {
        List<byte[]> bytes = new ArrayList<>();
        bytes.add(data);
        return FieldedTypeBuffer.create(bytes);
    }

    private static CasualBuffer createJsonBuffer(byte[] data)
    {
        List<byte[]> bytes = new ArrayList<>();
        bytes.add(data);
        return JsonBuffer.of(bytes);
    }

    private static CasualBuffer createOctetBuffer(byte[] data)
    {
        return OctetBuffer.of(data);
    }
}
