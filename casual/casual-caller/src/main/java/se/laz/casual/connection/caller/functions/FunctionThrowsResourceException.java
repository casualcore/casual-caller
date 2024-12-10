package se.laz.casual.connection.caller.functions;

import jakarta.resource.ResourceException;

@FunctionalInterface
public interface FunctionThrowsResourceException<R,T>
{
    R apply(T t) throws ResourceException;
}
