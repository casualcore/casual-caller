/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.http.test.resource;

import jakarta.ejb.Stateless;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.type.JsonBuffer;
import se.laz.casual.http.CasualContentType;
import se.laz.casual.http.CasualContentTypeConverter;

import java.io.InputStream;

@Stateless
@Path("/test")
public class TestResource
{
    public static final String X_OCTET = "application/casual-x-octet";
    public static final String JSON = "application/json";
    public static final String FIELD = "application/casual-field";
    public static final String STRING = "application/casual-string";
    public static final String NULL = "application/casual-null";

    @POST
    @Consumes(X_OCTET)
    @Path("{serviceName}")
    public Response serviceRequestCasualXOctet(@PathParam("serviceName") String serviceName, InputStream inputStream)
    {
        return createResponse(serviceName, inputStream, CasualContentType.X_OCTET);
    }
    @POST
    @Consumes(JSON)
    @Path("{serviceName}")
    public Response serviceRequestJson(@PathParam("serviceName") String serviceName, InputStream inputStream)
    {
        return createResponse(serviceName, inputStream, CasualContentType.JSON);
    }

    @POST
    @Consumes(FIELD)
    @Path("{serviceName}")
    public Response serviceRequestField(@PathParam("serviceName") String serviceName, InputStream inputStream)
    {
        return createResponse(serviceName, inputStream, CasualContentType.FIELD);
    }

    @POST
    @Consumes(STRING)
    @Path("{serviceName}")
    public Response serviceRequestCString(@PathParam("serviceName") String serviceName, InputStream inputStream)
    {
        return createResponse(serviceName, inputStream, CasualContentType.STRING);
    }

    private Response createResponse(String serviceName, InputStream inputStream, CasualContentType contentType)
    {
        MediaType type = CasualContentTypeConverter.convert(contentType);
        String typeAsString = type.getType() + "/" + type.getSubtype();
        return switch(TestPath.unmarshall(serviceName)){
            case OK -> Response.ok(inputStream).header(HttpHeaders.CONTENT_TYPE, typeAsString).build();
            case NOT_FOUND -> Response.status(Response.Status.NOT_FOUND).build();
            case ERROR -> Response.status(Response.Status.INTERNAL_SERVER_ERROR).header(HttpHeaders.CONTENT_TYPE, NULL).build();
            case ERROR_WITH_BODY -> responseWithAnswer(Response.Status.INTERNAL_SERVER_ERROR);
            case TIMEOUT -> Response.status(Response.Status.REQUEST_TIMEOUT).header(HttpHeaders.CONTENT_TYPE, NULL).build();
            case TIMEOUT_WITH_BODY -> responseWithAnswer(Response.Status.REQUEST_TIMEOUT);
        };
    }

    private Response responseWithAnswer(Response.Status status)
    {
        CasualBuffer errorBuffer = JsonBuffer.of("{\"msg\":\"error error!\"");
        StreamingOutput stream = outputStream -> outputStream.write(errorBuffer.getBytes().get(0));
        return Response.status(status).entity(stream).header(HttpHeaders.CONTENT_TYPE, JSON).build();
    }
}
