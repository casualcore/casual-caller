/*
 * Copyright (c) 2021, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import se.laz.casual.jca.CasualConnection;
import se.laz.casual.jca.CasualConnectionFactory;

import jakarta.resource.ResourceException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConnectionFactoryEntry
{
    private static final System.Logger LOG = System.getLogger(ConnectionFactoryEntry.class.getName());
    private final ConnectionFactoryProducer connectionFactoryProducer;
    private final AtomicBoolean needsDomainDiscovery = new AtomicBoolean(false);

    /**
     * Connection factory entries should invalidate on connection errors and revalidate as soon as a new valid
     * connection can be established.
     */
    private boolean valid = true;

    private ConnectionFactoryEntry(ConnectionFactoryProducer connectionFactoryProducer)
    {
        this.connectionFactoryProducer = connectionFactoryProducer;
    }

    public static ConnectionFactoryEntry of(ConnectionFactoryProducer connectionFactoryProducer)
    {
        Objects.requireNonNull(connectionFactoryProducer, "CasualConnectionFactoryProducer can not be null");
        return new ConnectionFactoryEntry(connectionFactoryProducer);
    }

    public String getJndiName()
    {
        return connectionFactoryProducer.getJndiName();
    }

    public CasualConnectionFactory getConnectionFactory()
    {
        return connectionFactoryProducer.getConnectionFactory();
    }

    public boolean isValid()
    {
        return valid;
    }

    public boolean isInvalid()
    {
        return !valid;
    }

    public void invalidate()
    {
        valid = false;
        LOG.log(System.Logger.Level.TRACE, () -> "Invalidated CasualConnection with jndiName=" + connectionFactoryProducer.getJndiName());

    }

    //Note: due to try with resources usage where we never use the resource
    @SuppressWarnings("try")
    public void validate()
    {
        try(CasualConnection con = getConnectionFactory().getConnection())
        {
            // We just want to check that a connection could be established to check connectivity
            valid = true;
            LOG.log(System.Logger.Level.TRACE, () -> "Successfully validated CasualConnection with jndiName=" + connectionFactoryProducer.getJndiName());
        }
        catch (ResourceException e)
        {
            // Failure to connect during validation should automatically invalidate ConnectionFactoryEntry
            valid = false;
            LOG.log(System.Logger.Level.WARNING, ()->"Failed validation of CasualConnection with jndiName=" + connectionFactoryProducer.getJndiName() + ", received error: " + e.getMessage());
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        ConnectionFactoryEntry that = (ConnectionFactoryEntry) o;
        return connectionFactoryProducer.equals(that.connectionFactoryProducer);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(connectionFactoryProducer);
    }

    @Override
    public String toString()
    {
        return "ConnectionFactoryEntry{" +
                "connectionFactoryProducer=" + connectionFactoryProducer +
                ", valid=" + valid +
                '}';
    }

    public void setNeedsDomainDiscovery(boolean value)
    {
        needsDomainDiscovery.set(value);
    }

    public boolean getNeedsDomainDiscovery()
    {
        return needsDomainDiscovery.getAndSet(false);
    }
}
