package se.laz.casual.connection.caller;

import java.util.Objects;
import java.util.UUID;

public record StickiedCallInfo(ConnectionFactoryEntry connectionFactoryEntry, UUID execution)
{
    public StickiedCallInfo
    {
        Objects.requireNonNull(connectionFactoryEntry, "connectionFactoryEntry can not be null");
        Objects.requireNonNull(execution, "execution can not be null");
    }
}
