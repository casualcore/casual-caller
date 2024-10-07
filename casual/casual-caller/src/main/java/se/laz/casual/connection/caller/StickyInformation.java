package se.laz.casual.connection.caller;

import java.util.Objects;
import java.util.UUID;

public class StickyInformation
{
    private final String poolName;
    private final UUID execution;
    private StickyInformation(String poolName, UUID execution)
    {
        this.poolName = poolName;
        this.execution = execution;
    }
    public static StickyInformation of(String poolName, UUID execution)
    {
        Objects.requireNonNull(poolName, "poolName can not be null");
        Objects.requireNonNull(execution, "execution can not be null");
        return new StickyInformation(poolName, execution);
    }
    public String getPoolName()
    {
        return poolName;
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
        if (!(o instanceof StickyInformation))
        {
            return false;
        }
        StickyInformation that = (StickyInformation) o;
        return Objects.equals(poolName, that.poolName) && Objects.equals(execution, that.execution);
    }
    @Override
    public int hashCode()
    {
        return Objects.hash(poolName, execution);
    }
    @Override
    public String toString()
    {
        return "StickyInformation{" +
                "poolName='" + poolName + '\'' +
                ", execution=" + execution +
                '}';
    }
}
