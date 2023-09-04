/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import se.laz.casual.connection.caller.config.ConfigurationService;
import se.laz.casual.jca.DomainId;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class TopologyChangedHandler
{
    private static final Logger LOG = Logger.getLogger(TopologyChangedHandler.class.getName());
    @Resource
    private ManagedScheduledExecutorService scheduledExecutorService;
    private final Set<DomainId> changedDomains = ConcurrentHashMap.newKeySet();
    private CacheRepopulator cacheRepopulator;
    private Supplier<List<ConnectionFactoryEntry>> connectionFactoryEntrySupplier;

    // wls NOP-constructor
    public TopologyChangedHandler()
    {}

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
        long delayInMs = ConfigurationService.getInstance().getConfiguration().getTopologyChangeDelayMillis();
        try
        {
            DiscoveryTask task = new DiscoveryTask(domainId, changedDomains::remove, connectionFactoryEntrySupplier, cacheRepopulator);
            scheduledExecutorService.schedule( task, delayInMs, TimeUnit.MILLISECONDS);
        }
        catch(RejectedExecutionException e)
        {
            changedDomains.remove(domainId);
            markForLaterDomainDiscovery(domainId);
            LOG.log(Level.WARNING, e, () -> "Could not schedule task to handle topology change for domain: " + domainId + " it will be handled on the next tpcall/tpacall or enqueue/dequeue call");
        }
    }

    public void setManagedScheduledExecutorService(ManagedScheduledExecutorService scheduledExecutorService)
    {
        this.scheduledExecutorService = scheduledExecutorService;
    }

    private void markForLaterDomainDiscovery(DomainId domainId)
    {
        Optional<ConnectionFactoryEntry> maybeMatch = connectionFactoryEntrySupplier.get().stream()
                                                                                    .filter(connectionFactoryEntry -> DomainIdChecker.isSameDomain(domainId, connectionFactoryEntry))
                                                                                    .findFirst();
        maybeMatch.ifPresent(connectionFactoryEntry -> connectionFactoryEntry.setNeedsDomainDiscovery(true));
    }

    private class DiscoveryTask implements Runnable
    {
        private final DomainId domainId;
        private final Consumer<DomainId> topologyChangeHandledConsumer;
        private final Supplier<List<ConnectionFactoryEntry>> connectionFactoryEntrySupplier;
        private final CacheRepopulator cacheRepopulator;
        public DiscoveryTask(DomainId domainId, Consumer<DomainId> topologyChangeHandledConsumer, Supplier<List<ConnectionFactoryEntry>> connectionFactoryEntrySupplier, CacheRepopulator cacheRepopulator)
        {
            this.domainId = domainId;
            this.topologyChangeHandledConsumer = topologyChangeHandledConsumer;
            this.connectionFactoryEntrySupplier = connectionFactoryEntrySupplier;
            this.cacheRepopulator = cacheRepopulator;
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
                LOG.log(Level.WARNING, e, () -> "Failed handling topology update, most likely connection went away. Will be handled when connection is reestablished. Domain: " + domainId);
            }
        }
        private void handleTopologyChanged(final DomainId domainId)
        {
            topologyChangeHandledConsumer.accept(domainId);
            Optional<ConnectionFactoryEntry> maybeMatch = connectionFactoryEntrySupplier.get().stream()
                                                                                        .filter(connectionFactoryEntry -> DomainIdChecker.isSameDomain(domainId, connectionFactoryEntry))
                                                                                        .findFirst();
            maybeMatch.ifPresent(cacheRepopulator::repopulate);
            // if no match, then that connection is gone and the cache will be repopulated once it re-establishes a connection
        }
    }

}
