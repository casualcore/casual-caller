/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import se.laz.casual.jca.CasualConnection;
import se.laz.casual.jca.ConnectionObserver;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionObserverHandler
{
    private static final Logger LOG = Logger.getLogger(ConnectionObserverHandler.class.getName());
    public static ConnectionObserverHandler of()
    {
        return new ConnectionObserverHandler();
    }

    public void addObserver(ConnectionFactoryEntry connectionFactoryEntry, ConnectionObserver connectionObserver)
    {
        if(connectionFactoryEntry.isInvalid())
        {
            // will be handled when connection is reestablished
            return;
        }
        try(CasualConnection casualConnection = connectionFactoryEntry.getConnectionFactory().getConnection())
        {
            casualConnection.addConnectionObserver(connectionObserver);
        }
        catch (Exception e)
        {
            connectionFactoryEntry.invalidate();
            LOG.log(Level.FINE, e, () -> "Failed registering connection observer for " + connectionFactoryEntry.getJndiName());
        }
    }
}
