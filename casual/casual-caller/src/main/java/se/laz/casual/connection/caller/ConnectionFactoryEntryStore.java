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
import se.laz.casual.jca.CasualConnection;
import se.laz.casual.jca.ConnectionObserver;
import se.laz.casual.jca.DomainId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ConnectionFactoryEntryStore implements ConnectionObserver
{
    private static final Logger LOG = Logger.getLogger(ConnectionFactoryEntryStore.class.getName());
    private final ConnectionFactoryFinder connectionFactoryFinder;
    private final TopologyChangedHandler topologyChangedHandler;
    private List<ConnectionFactoryEntry> connectionFactories = Collections.emptyList();
    private ConnectionObserverHandler connectionObserverHandler;
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

    public List<ConnectionFactoryEntry> get()
    {
        if(connectionFactories.isEmpty())
        {
            initialize();
            if(connectionFactories.isEmpty())
            {
                LOG.warning(() -> "could not find any connection factories, casual-caller will not work. Will retry on next access.\n Either your configuration is wrong or the entries do not yet exist in the JNDI-tree just yet.");
            }
        }
        if(reverseEntries.isEmpty())
        {
            return Collections.unmodifiableList(connectionFactories);
        }
        List<ConnectionFactoryEntry> entries = new ArrayList<>();
        connectionFactories.stream()
                           .filter(entry -> !reverseEntries.containsKey(entry))
                           .forEach(entries::add);
        reverseEntries.values().forEach(entriesByDomain -> entries.addAll(entriesByDomain.values()));
        return Collections.unmodifiableList(entries);
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
        connectionFactories.forEach(entry -> refreshReverseEntries(entry, added, purged));
        return new ReverseRefreshResult(added, purged);
    }

    private void refreshReverseEntries(ConnectionFactoryEntry base, List<ConnectionFactoryEntry> added, List<ConnectionFactoryEntry> purged)
    {
        List<DomainId> domainIds;
        try(CasualConnection connection = base.getConnectionFactory().getConnection())
        {
            if(!connection.isReversePool())
            {
                return;
            }
            domainIds = connection.getPoolDomainIds();
        }
        catch(Exception e)
        {
            if(!reverseEntries.containsKey(base))
            {
                // maybe a reverse pool with no instances connected yet, maybe a normal connection
                // factory that is currently down - normal validation handles it either way
                return;
            }
            // a known reverse base with no connection available means no instances are connected
            LOG.log(Level.FINEST, e, () -> "no instances connected for reverse pool backed entry: " + base.getJndiName());
            domainIds = Collections.emptyList();
        }
        if(!reverseEntries.containsKey(base))
        {
            // newly added as reverse pool - it is no longer served directly and
            // anything cached for it, from before - needs to go
            purged.add(base);
        }
        Map<DomainId, ConnectionFactoryEntry> entriesByDomain = reverseEntries.computeIfAbsent(base, key -> new ConcurrentHashMap<>());
        for(DomainId domainId : domainIds)
        {
            if(!entriesByDomain.containsKey(domainId))
            {
                ConnectionFactoryEntry entry = ConnectionFactoryEntry.of(ReverseConnectionFactoryProducer.of(base, domainId));
                entriesByDomain.put(domainId, entry);
                added.add(entry);
                LOG.info(() -> "reverse inbound instance connected, adding entry: " + entry.getJndiName());
            }
        }
        for(DomainId knownDomainId : new ArrayList<>(entriesByDomain.keySet()))
        {
            if(!domainIds.contains(knownDomainId))
            {
                ConnectionFactoryEntry removed = entriesByDomain.remove(knownDomainId);
                removed.invalidate();
                purged.add(removed);
                LOG.info(() -> "reverse inbound instance gone, removing entry: " + removed.getJndiName());
            }
        }
    }

    @PostConstruct
    public synchronized void initialize()
    {
        connectionFactories = connectionFactoryFinder.findConnectionFactory(getJndiRoot());
        topologyChangedHandler.setSupplier(this::get);
        refreshReverseEntries().added().forEach(this::addConnectionObserver);
        connectionFactories.forEach(this::addConnectionObserver);
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

    public void setConnectionObserverHandler(ConnectionObserverHandler connectionObserverHandler)
    {
        this.connectionObserverHandler = connectionObserverHandler;
    }

    private ConnectionObserverHandler getConnectionObserverHandler()
    {
        if(null == connectionObserverHandler)
        {
            setConnectionObserverHandler(ConnectionObserverHandler.of());
        }
        return connectionObserverHandler;
    }

    private String getJndiRoot()
    {
        return ConfigurationService.getInstance().getConfiguration().getJndiSearchRoot();
    }


}
