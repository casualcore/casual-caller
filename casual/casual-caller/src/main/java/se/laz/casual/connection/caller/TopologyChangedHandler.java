package se.laz.casual.connection.caller;

import javax.inject.Inject;
import javax.resource.ResourceException;
import se.laz.casual.jca.CasualConnection;
import se.laz.casual.jca.DomainId;

import java.util.List;
import java.util.Optional;

public class TopologyChangedHandler
{
    private CacheRepopulator cacheRepopulator;

    public TopologyChangedHandler()
    {
        // public NOP-constructor needed for wls-only
        cacheRepopulator = null;
    }

    @Inject
    public TopologyChangedHandler(CacheRepopulator cacheRepopulator)
    {
        this.cacheRepopulator = cacheRepopulator;
    }

    public void topologyChanged(DomainId domainId, List<ConnectionFactoryEntry> connectionFactoryEntries)
    {
        try
        {
            handleTopologyChanged(domainId, connectionFactoryEntries);
        }
        catch(Exception e)
        {
            // NOP
        }

    }

    private void handleTopologyChanged(final DomainId domainId, List<ConnectionFactoryEntry> connectionFactoryEntries)
    {
        Optional<ConnectionFactoryEntry> maybeMatch = connectionFactoryEntries.stream()
                                                                              .filter(connectionFactoryEntry -> sameDomain(domainId, connectionFactoryEntry))
                                                                              .findFirst();
        maybeMatch.ifPresent(cacheRepopulator::repopulate);
        // if no match, then that connection is gone and the cache will be repopulated once it re-establishes a connection
    }

    private boolean sameDomain(DomainId domainId, ConnectionFactoryEntry connectionFactoryEntry)
    {
        try(CasualConnection casualConnection = connectionFactoryEntry.getConnectionFactory().getConnection())
        {
            if(domainId == casualConnection.getDomainId())
            {
                return true;
            }
        }
        catch(ResourceException e)
        {
            // NOP
        }
        return false;
    }

}
