/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.http;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.CasualBufferType;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.buffer.type.ServiceBuffer;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.flags.ServiceReturnState;

import java.io.Closeable;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

import static jakarta.ws.rs.client.Entity.entity;

public final class HttpClient implements Closeable
{
    private final Client client;
    private HttpClient()
    {
        this.client = ClientBuilder.newClient();
    }

    public static HttpClient of()
    {
        return new HttpClient();
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public ServiceReturn<CasualBuffer> request(URI uri, CasualBuffer buffer)
    {
        Objects.requireNonNull(uri, "uri can not be null");
        Objects.requireNonNull(buffer, "buffer can not be null");
        MediaType mediaType = CasualBufferTypeConverter.convert(CasualBufferType.unmarshall(buffer.getType()));
        Response response = client.target(uri).request(mediaType).post(entity(buffer.getBytes().get(0), mediaType));
        ErrorState errorState = ResponseStatusConverter.convert(response.getStatusInfo().toEnum());
        if (errorState != ErrorState.OK)
        {
            return errorResponse(errorState, response);
        }
        Optional<CasualBuffer> responseBuffer = ResponseConverter.convert(response);
        return responseBuffer.map(casualBuffer -> new ServiceReturn<>(casualBuffer, ServiceReturnState.TPSUCCESS, ErrorState.OK, 0))
                             .orElseGet(() -> new ServiceReturn<>(ServiceBuffer.empty(), ServiceReturnState.TPSUCCESS, ErrorState.OK, 0));

    }

    private ServiceReturn<CasualBuffer> errorResponse(ErrorState errorState, Response response)
    {
        Optional<CasualBuffer> responseBuffer = ResponseConverter.convert(response);
        return responseBuffer.map(casualBuffer -> new ServiceReturn<>(casualBuffer, ServiceReturnState.TPFAIL, errorState, 0))
                             .orElseGet(() -> new ServiceReturn<>(ServiceBuffer.empty(), ServiceReturnState.TPFAIL, errorState, 0));
    }

    @Override
    public void close()
    {
        client.close();
    }
}
