/*
 * Copyright (c) 2021, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import se.laz.casual.jca.CasualConnection;
import se.laz.casual.jca.CasualConnectionFactory;
import se.laz.casual.jca.ConnectionListener;

import javax.resource.ResourceException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionFactoryEntry implements ConnectionListener
{
    private static final Logger LOG = Logger.getLogger(ConnectionFactoryEntry.class.getName());
    private final ConnectionFactoryProducer connectionFactoryProducer;
    private final AtomicBoolean connectionListenerAdded = new AtomicBoolean(false);
    private final AtomicBoolean connectionEnabled = new AtomicBoolean(true);
    /**
     * Connection factory entries should invalidate on connection errors and revalidate as soon as a new valid
     * connection can be established.
     * For casual protocol >= v1.1 - if the connection is disabled, this also means that it is not valid
     * until enabled.
     * It is enabled when the actual connection is closed, if it was disabled due to domain disconnecting.
     */
    private boolean valid = true;

    private ConnectionFactoryEntry(ConnectionFactoryProducer connectionFactoryProducer)
    {
        this.connectionFactoryProducer = connectionFactoryProducer;
    }

    public static ConnectionFactoryEntry of(ConnectionFactoryProducer connectionFactoryProducer)
    {
        Objects.requireNonNull(connectionFactoryProducer, "CasualConnectionFactoryProducer can not be null");
        ConnectionFactoryEntry connectionFactoryEntry = new ConnectionFactoryEntry(connectionFactoryProducer);
        connectionFactoryEntry.validate();
        return connectionFactoryEntry;
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
        return valid && connectionEnabled.get();
    }

    public boolean isInvalid()
    {
        return !isValid();
    }

    public void invalidate()
    {
        valid = false;
        LOG.finest(() -> "Invalidated CasualConnection with jndiName=" + connectionFactoryProducer.getJndiName());
    }

    //Note: due to try with resources usage where we never use the resource
    @SuppressWarnings("try")
    public void validate()
    {
        try(CasualConnection con = getConnectionFactory().getConnection())
        {
            maybeAddConnectionListener(con);
            // We just want to check that a connection could be established to check connectivity
            valid = true;
            LOG.finest(() -> "Successfully validated CasualConnection with jndiName=" + connectionFactoryProducer.getJndiName());
        }
        catch (ResourceException e)
        {
            // Failure to connect during validation should automatically invalidate ConnectionFactoryEntry
            valid = false;
            LOG.log(Level.WARNING, e, ()->"Failed validation of CasualConnection with jndiName=" + connectionFactoryProducer.getJndiName() + ", received error: " + e.getMessage());
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
        return isValid() == that.isValid() && connectionFactoryProducer.equals(that.connectionFactoryProducer);
    }

    @Override
    public int hashCode()
    {
        // TODO: why is isValid in hashCode?
        return Objects.hash(connectionFactoryProducer, isValid());
    }

    @Override
    public String toString()
    {
        return "ConnectionFactoryEntry{" +
                "connectionFactoryProducer=" + connectionFactoryProducer +
                ", valid=" + valid +
                ", connectionEnabled=" + connectionEnabled +
                '}';
    }

    @Override
    public void connectionDisabled()
    {
        connectionEnabled.set(true);
    }

    @Override
    public void connectionEnabled()
    {
        connectionEnabled.set(false);
    }

    private void maybeAddConnectionListener(CasualConnection connection)
    {
        if(connectionListenerAdded.get())
        {
            return;
        }
        connection.addListener(this);
        connectionListenerAdded.set(true);
    }
}
