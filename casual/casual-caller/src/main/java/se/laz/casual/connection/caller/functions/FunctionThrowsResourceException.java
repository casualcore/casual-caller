package se.laz.casual.connection.caller.functions;

import jakarta.resource.ResourceException;
import se.laz.casual.jca.CasualConnection;

import java.util.UUID;

@FunctionalInterface
public interface FunctionThrowsResourceException <R>
{
    R apply(CasualConnection connection, UUID execution) throws ResourceException;
}
