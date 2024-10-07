package se.laz.casual.connection.caller;

import java.util.Objects;
import java.util.UUID;

public class StickiedCallInfo
{
    private final ConnectionFactoryEntry connectionFactoryEntry;
    private final UUID execution;
    private StickiedCallInfo(ConnectionFactoryEntry connectionFactoryEntry, UUID execution)
    {
        this.connectionFactoryEntry = connectionFactoryEntry;
        this.execution = execution;
    }

    public static StickiedCallInfo of(ConnectionFactoryEntry connectionFactoryEntry, UUID execution)
    {
        Objects.requireNonNull(connectionFactoryEntry, "connectionFactoryEntry can not be null");
        Objects.requireNonNull(execution, "execution can not be null");
        return new StickiedCallInfo(connectionFactoryEntry, execution);
    }

    public ConnectionFactoryEntry getConnectionFactoryEntry()
    {
        return connectionFactoryEntry;
    }

    public UUID getExecution()
    {
        return execution;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof StickiedCallInfo))
        {
            return false;
        }
        StickiedCallInfo that = (StickiedCallInfo) o;
        return Objects.equals(connectionFactoryEntry, that.connectionFactoryEntry) && Objects.equals(execution, that.execution);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(connectionFactoryEntry, execution);
    }

    @Override
    public String toString()
    {
        return "StickiedCallInfo{" +
                "connectionFactoryEntry=" + connectionFactoryEntry +
                ", execution=" + execution +
                '}';
    }
}
