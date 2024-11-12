package se.laz.casual.http;

import jakarta.ws.rs.core.Response;
import se.laz.casual.api.flags.ErrorState;

public class ResponseStatusConverter
{
    private ResponseStatusConverter()
    {}

    public static ErrorState convert(Response.Status status)
    {
        return switch (status){
            case OK -> ErrorState.OK;
            case NOT_FOUND -> ErrorState.TPENOENT;
            case REQUEST_TIMEOUT -> ErrorState.TPETIME;
            default -> ErrorState.TPESVCERR;
        };
    }
}
