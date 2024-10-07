package se.laz.casual.connection.caller;

import java.util.Objects;
import java.util.UUID;

public record StickyInformation(String poolName, UUID execution)
{
    public StickyInformation
    {
        Objects.requireNonNull(poolName, "poolName can not be null");
        Objects.requireNonNull(execution, "execution can not be null");
    }
}
