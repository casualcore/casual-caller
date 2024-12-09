package se.laz.casual.connection.caller.services;

import se.laz.casual.api.CasualRuntimeException;

public class ServiceRouteReaderException extends CasualRuntimeException
{
    private static final long serialVersionUID = 1L;

    public ServiceRouteReaderException(String message, Throwable t)
    {
        super(message, t);
    }
}
