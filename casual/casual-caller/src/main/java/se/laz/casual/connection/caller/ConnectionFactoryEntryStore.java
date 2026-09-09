/*
 * Copyright (c) 2017 - 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.laz.casual.connection.caller.config.ConfigurationService;
import se.laz.casual.jca.CasualConnectionFactory;
import se.laz.casual.jca.ConnectionObserver;
import se.laz.casual.jca.DomainId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
public class ConnectionFactoryEntryStore implements ConnectionObserver
{
    private static final Logger LOG = Logger.getLogger(ConnectionFactoryEntryStore.class.getName());
    private final ConnectionFactoryFinder connectionFactoryFinder;
    private final TopologyChangedHandler topologyChangedHandler;
    private List<ConnectionFactoryEntry> normalEntries = Collections.emptyList();
    // we keep the reverse bases separate, these are never used as is but via a domain that has connected ( virtual pools)
    private List<ConnectionFactoryEntry> reverseBases = Collections.emptyList();
    private final Object lock = new Object();
    private ConnectionObserverHandler connectionObserverHandler = ConnectionObserverHandler.of();
    // reverse pool backed entries are never served directly, each of their currently connected
    // instances is served as its own entry - keyed by the base entry known via configuration as reverse
    // the domain ids from the reverse inbound connections are used to map to entries that can actually be used for outbound calls
    private final Map<ConnectionFactoryEntry, Map<DomainId, ConnectionFactoryEntry>> reverseEntries = new ConcurrentHashMap<>();

    public ConnectionFactoryEntryStore()
    {
        // public NOP-constructor needed for wls-only
        connectionFactoryFinder = null;
        topologyChangedHandler = null;
    }

    @Inject
    public ConnectionFactoryEntryStore(ConnectionFactoryFinder connectionFactoryFinder, TopologyChangedHandler topologyChangedHandler)
    {
        this.connectionFactoryFinder = connectionFactoryFinder;
        this.topologyChangedHandler = topologyChangedHandler;
    }

    /**
     * Returns whether you have any configured connection factories.
     *
     * <p>This method synchronizes access to the configured factory collections.
     * An empty reverse pool counts as a configured factory.
     *
     * @return {@code true} if, at least, a normal factory or reverse base exists
     */
    public boolean hasConfiguredFactories()
    {
        synchronized (lock)
        {
            if (normalEntries.isEmpty() && reverseBases.isEmpty())
            {
                initialize();
            }
            return !normalEntries.isEmpty() || !reverseBases.isEmpty();
        }
    }


    public List<ConnectionFactoryEntry> get()
    {
        synchronized (lock)
        {
            if (normalEntries.isEmpty() && reverseBases.isEmpty())
            {
                initialize();
                if (normalEntries.isEmpty() && reverseBases.isEmpty())
                {
                    LOG.warning(
                            "No connection factories found. Retrying on next access.");
                }
            }
            List<ConnectionFactoryEntry> entries =
                    new ArrayList<>(normalEntries);
            reverseEntries.values()
                    .forEach(entriesByDomain ->
                            entries.addAll(entriesByDomain.values()));

            return Collections.unmodifiableList(entries);
        }
    }

    /**
     * Refresh the per instance entries of reverse pool backed connection factories.
     * With n instances connected to a reverse pool there are n entries, each acting as if its
     * instance had been configured as its own pool. New instances yield new entries, entries of
     * gone instances are invalidated and removed.
     */
    public ReverseRefreshResult refreshReverseEntries()
    {
        List<ConnectionFactoryEntry> added = new ArrayList<>();
        List<ConnectionFactoryEntry> purged = new ArrayList<>();
        synchronized (lock)
        {
            reverseBases.forEach(entry -> refreshReverseEntries(entry, added, purged));
            return new ReverseRefreshResult(added, purged);
        }
    }

    private void refreshReverseEntries(ConnectionFactoryEntry base, List<ConnectionFactoryEntry> added, List<ConnectionFactoryEntry> purged)
    {
        List<DomainId> domainIds = base.getConnectionFactory().getDomainIds();
        Map<DomainId, ConnectionFactoryEntry> entriesByDomain = reverseEntries.computeIfAbsent(base, key -> new ConcurrentHashMap<>());
        for(DomainId domainId : domainIds)
        {
            ConnectionFactoryEntry existing = entriesByDomain.get(domainId);
            if(existing == null)
            {
                ConnectionFactoryEntry entry = ConnectionFactoryEntry.of(ReverseConnectionFactoryProducer.of(base, domainId));
                if(entriesByDomain.putIfAbsent(domainId, entry) == null)
                {
                    added.add(entry);
                    LOG.info(() -> "reverse inbound instance connected, adding entry: " + entry.getJndiName());
                }
            }
        }
        for(DomainId knownDomainId : new ArrayList<>(entriesByDomain.keySet()))
        {
            if(!domainIds.contains(knownDomainId))
            {
                ConnectionFactoryEntry removed = entriesByDomain.remove(knownDomainId);
                if(removed != null)
                {
                    removed.invalidate();
                    purged.add(removed);
                    LOG.info(() -> "reverse inbound instance gone, removing entry: " + removed.getJndiName());
                }
            }
        }
    }

    @PostConstruct
    public void initialize()
    {
        synchronized (lock)
        {
            // preserve existing virtual entries if initialization runs again.
            if (!normalEntries.isEmpty() || !reverseBases.isEmpty())
            {
                return;
            }
            List<ConnectionFactoryEntry> discovered =
                    connectionFactoryFinder.findConnectionFactory(getJndiRoot());
            List<ConnectionFactoryEntry> normal = new ArrayList<>();
            List<ConnectionFactoryEntry> reverse = new ArrayList<>();
            for (ConnectionFactoryEntry entry : discovered)
            {
                CasualConnectionFactory factory = entry.getConnectionFactory();
                if (factory.isReverse())
                {
                    reverse.add(entry);
                }
                else
                {
                    normal.add(entry);
                }
            }
            normalEntries = normal;
            reverseBases = reverse;

            topologyChangedHandler.setSupplier(this::get);

            refreshReverseEntries().added().forEach(this::addConnectionObserver);
            normalEntries.forEach(this::addConnectionObserver);
        }
    }

    public void addConnectionObserver(ConnectionFactoryEntry connectionFactoryEntry)
    {
        getConnectionObserverHandler().addObserver(connectionFactoryEntry, this);
    }

    @Override
    public void topologyChanged(DomainId domainId)
    {
        topologyChangedHandler.topologyChanged(domainId);
    }

    void setConnectionObserverHandler(ConnectionObserverHandler connectionObserverHandler)
    {
        Objects.requireNonNull(connectionObserverHandler, "connectionObserverHandler can not be null");
        this.connectionObserverHandler = connectionObserverHandler;
    }

    private ConnectionObserverHandler getConnectionObserverHandler()
    {
        return connectionObserverHandler;
    }

    private String getJndiRoot()
    {
        return ConfigurationService.getInstance().getConfiguration().getJndiSearchRoot();
    }


}
