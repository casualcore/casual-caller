/*
 * Copyright (c) 2023 - 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.topologychanged;

import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.laz.casual.connection.caller.CacheRepopulator;
import se.laz.casual.connection.caller.CasualCallerException;
import se.laz.casual.connection.caller.ConnectionFactoryEntry;
import se.laz.casual.connection.caller.DomainIdChecker;
import se.laz.casual.connection.caller.TopologyChangedHandler;
import se.laz.casual.connection.caller.config.ConfigurationService;
import se.laz.casual.jca.DomainId;
import se.laz.casual.jca.RuntimeInformation;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class TopologyChangedHandlerImpl implements TopologyChangedHandler
{
    private static final Logger LOG = Logger.getLogger(TopologyChangedHandlerImpl.class.getName());
    @Resource
    private ManagedScheduledExecutorService scheduledExecutorService;
    private CacheRepopulator cacheRepopulator;
    private Supplier<List<ConnectionFactoryEntry>> connectionFactoryEntrySupplier;

    private AccumulatingTopologyUpdateStore updateStore;

    // wls NOP-constructor
    public TopologyChangedHandlerImpl()
    {}

    @Inject
    public TopologyChangedHandlerImpl(CacheRepopulator cacheRepopulator)
    {
        this.cacheRepopulator = cacheRepopulator;
        long delayInMs = ConfigurationService.getInstance().getConfiguration().getTopologyChangeDelayMillis();
        updateStore = new AccumulatingTopologyUpdateStore( delayInMs );
    }

    @Override
    public void setSupplier(Supplier<List<ConnectionFactoryEntry>> connectionFactoryEntrySupplier)
    {
        this.connectionFactoryEntrySupplier = connectionFactoryEntrySupplier;
        // we are now ready to handle topology updates.
        scheduleTopologyUpdateTask();
    }

    @Override
    public void topologyChanged(final DomainId domainId)
    {
        updateStore.put( domainId );
    }

    public void setManagedScheduledExecutorService(ManagedScheduledExecutorService scheduledExecutorService)
    {
        this.scheduledExecutorService = scheduledExecutorService;
    }

    private void scheduleTopologyUpdateTask()
    {
        try
        {
            DiscoveryTask task = new DiscoveryTask( connectionFactoryEntrySupplier, cacheRepopulator);
            scheduledExecutorService.schedule( task, 0L, TimeUnit.MILLISECONDS);
        }
        catch(RejectedExecutionException e)
        {
            throw new CasualCallerException( "Could not schedule task to handle topology change.", e );
        }
    }

    private class DiscoveryTask implements Runnable
    {
        private final Supplier<List<ConnectionFactoryEntry>> connectionFactoryEntrySupplier;
        private final CacheRepopulator cacheRepopulator;
        public DiscoveryTask(Supplier<List<ConnectionFactoryEntry>> connectionFactoryEntrySupplier, CacheRepopulator cacheRepopulator)
        {
            this.connectionFactoryEntrySupplier = connectionFactoryEntrySupplier;
            this.cacheRepopulator = cacheRepopulator;
        }

        @Override
        public void run()
        {
            LOG.finest(() -> "starting topology update task" );
            while( !RuntimeInformation.isDomainBeingShutdown() )
            {
                DomainId domainId = updateStore.take();
                try
                {
                    handleTopologyChanged( domainId );
                }
                catch(Exception e)
                {
                    // catching since this method lives in a timer that should never ever throw
                    LOG.log(Level.WARNING, e, () -> "Failed handling topology update, most likely connection went away. Will be handled when connection is reestablished. Domain: " + domainId);
                }
            }
        }

        private void handleTopologyChanged(final DomainId domainId)
        {
            Optional<ConnectionFactoryEntry> maybeMatch = connectionFactoryEntrySupplier.get().stream()
                                                                                        .filter(connectionFactoryEntry -> DomainIdChecker.isSameDomain(domainId, connectionFactoryEntry))
                                                                                        .findFirst();
            LOG.finest(() -> "will issue domain discovery for domain: " + domainId);
            maybeMatch.ifPresent(cacheRepopulator::repopulate);
            LOG.finest(() -> "domain discovery finished for domain: " + domainId);
            // if no match, then that connection is gone and the cache will be repopulated once it re-establishes a connection
        }
    }

}
