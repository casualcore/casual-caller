package se.laz.casual.connection.caller;

import jakarta.resource.ResourceException;
import se.laz.casual.jca.CasualConnection;
import se.laz.casual.jca.ConnectionObserver;

public class ConnectionObserverHandler
{

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
        catch (ResourceException e)
        {
            // NOP
            // will be handled when connection is reestablished
        }
    }
}
