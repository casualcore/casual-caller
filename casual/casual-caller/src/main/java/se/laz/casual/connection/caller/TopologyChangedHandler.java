package se.laz.casual.connection.caller;

import se.laz.casual.jca.CasualConnection;
import se.laz.casual.jca.DomainId;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.inject.Inject;
import javax.resource.ResourceException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TopologyChangedHandler
{
    private static final Logger LOG = Logger.getLogger(TopologyChangedHandler.class.getName());
    @Resource
    private ManagedScheduledExecutorService scheduledExecutorService;
    private Set<DomainId> changedDomains = ConcurrentHashMap.newKeySet();
    private CacheRepopulator cacheRepopulator;
    private ConnectionFactoryEntryStore entryStore;

    public TopologyChangedHandler()
    {
        // public NOP-constructor needed for wls-only
        cacheRepopulator = null;
    }

    @Inject
    public TopologyChangedHandler(CacheRepopulator cacheRepopulator, ConnectionFactoryEntryStore entryStore)
    {
        this.cacheRepopulator = cacheRepopulator;
        this.entryStore = entryStore;
    }

    @PostConstruct
    public void setup()
    {
        // TODO:
        // get delay from configuration
        long delayInMs = 50;
        scheduledExecutorService.scheduleWithFixedDelay(this::handleTopologyChanged, delayInMs, delayInMs, TimeUnit.MILLISECONDS);
    }

    public void topologyChanged(DomainId domainId)
    {
        changedDomains.add(domainId);
    }

    private void handleTopologyChanged()
    {
        changedDomains.forEach(domainId -> {
            try
            {
                // removing first in case this domain has gone away before we could issue the discovery
                // we do not want to keep retrying towards something that is gone
                // cache coherence is handled on domain reconnect
                changedDomains.remove(domainId);
                handleTopologyChanged(domainId, entryStore.get());
            }
            catch(Exception e)
            {
                // catching since this method lives in a timer that should never ever throw
                LOG.log(Level.WARNING, e, () -> "Failed handling topology update. Cache will not be in a good state until next update or disconnect/reconnect");
            }
        });
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
