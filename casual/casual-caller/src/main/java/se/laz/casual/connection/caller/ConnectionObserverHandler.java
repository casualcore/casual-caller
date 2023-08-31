package se.laz.casual.connection.caller;

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
        catch (Exception e)
        {
            // NOP
            // will be handled whenever the connection is reestablished
        }
    }
}
