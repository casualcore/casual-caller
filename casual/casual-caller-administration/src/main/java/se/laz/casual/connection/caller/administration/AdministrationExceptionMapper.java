package se.laz.casual.connection.caller.administration;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import java.net.HttpURLConnection;

public class AdministrationExceptionMapper implements ExceptionMapper<Exception> {
    @Override
    public Response toResponse(Exception e) {
        return Response.status(HttpURLConnection.HTTP_INTERNAL_ERROR).entity(e).build();
    }
}
