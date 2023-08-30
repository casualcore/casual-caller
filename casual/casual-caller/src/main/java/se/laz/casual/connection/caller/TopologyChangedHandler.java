package se.laz.casual.connection.caller;

import se.laz.casual.jca.CasualConnection;
import se.laz.casual.jca.DomainId;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.inject.Inject;
import javax.resource.ResourceException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TopologyChangedHandler
{
    private static final Logger LOG = Logger.getLogger(TopologyChangedHandler.class.getName());
    @Resource
    private ManagedScheduledExecutorService scheduledExecutorService;
    private Set<DomainId> changedDomains = ConcurrentHashMap.newKeySet();
    private CacheRepopulator cacheRepopulator;
    private Supplier<List<ConnectionFactoryEntry>> connectionFactoryEntrySupplier;

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

    public void setSupplier(Supplier<List<ConnectionFactoryEntry>> connectionFactoryEntrySupplier)
    {
        this.connectionFactoryEntrySupplier = connectionFactoryEntrySupplier;
    }

    public void topologyChanged(final DomainId domainId)
    {
        if(changedDomains.contains(domainId))
        {
            return;
        }
        changedDomains.add(domainId);
        // TODO:
        // Get delay from configuration
        long delayInMs = 50;
        scheduledExecutorService.schedule( new DiscoveryTask(domainId), delayInMs, TimeUnit.MILLISECONDS);
    }

    private void handleTopologyChanged(final DomainId domainId)
    {
        changedDomains.remove(domainId);
        Optional<ConnectionFactoryEntry> maybeMatch = connectionFactoryEntrySupplier.get().stream()
                                                                                    .filter(connectionFactoryEntry -> isSameDomain(domainId, connectionFactoryEntry))
                                                                                    .findFirst();
        maybeMatch.ifPresent(cacheRepopulator::repopulate);
        // if no match, then that connection is gone and the cache will be repopulated once it re-establishes a connection
    }

    private boolean isSameDomain(DomainId domainId, ConnectionFactoryEntry connectionFactoryEntry)
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

    private class DiscoveryTask implements Runnable
    {
        private final DomainId domainId;
        public DiscoveryTask(DomainId domainId)
        {
            this.domainId = domainId;
        }
        @Override
        public void run()
        {
            try
            {
                handleTopologyChanged(domainId);
            }
            catch(Exception e)
            {
                // catching since this method lives in a timer that should never ever throw
                LOG.log(Level.WARNING, e, () -> "Failed handling topology update, most likely connection went away. Cache will not be in a good state until next update or disconnect/reconnect. Domain: " + domainId);
            }
        }
    }

}
