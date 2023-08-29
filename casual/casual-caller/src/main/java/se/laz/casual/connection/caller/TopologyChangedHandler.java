package se.laz.casual.connection.caller;

import javax.inject.Inject;
import javax.resource.ResourceException;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import se.laz.casual.jca.CasualConnection;
import se.laz.casual.jca.DomainId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TopologyChangedHandler
{
    private static final Logger LOG = Logger.getLogger(TopologyChangedHandler.class.getName());
    @Resource()
    private ManagedExecutorService executorService;
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
        CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> handleTopologyChanged(domainId, connectionFactoryEntries), executorService);
        future.whenComplete((v, e) ->{
            if(null != e){
                LOG.log(Level.WARNING, e, () -> "failed handling topology changed event for domain: " + domainId);
            }else{
                LOG.finest(() -> "handled topology changed event for domain: " + domainId);
            }
        });
    }

    private Void handleTopologyChanged(final DomainId domainId, List<ConnectionFactoryEntry> connectionFactoryEntries)
    {
        Optional<ConnectionFactoryEntry> maybeMatch = connectionFactoryEntries.stream()
                                                                              .filter(connectionFactoryEntry -> sameDomain(domainId, connectionFactoryEntry))
                                                                              .findFirst();
        maybeMatch.ifPresent(cacheRepopulator::repopulate);
        // if no match, then that connection is gone and the cache will be repopulated once it re-establishes a connection
        return null;
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
