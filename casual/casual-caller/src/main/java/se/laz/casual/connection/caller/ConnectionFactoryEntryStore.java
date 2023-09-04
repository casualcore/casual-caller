/*
 * Copyright (c) 2017 - 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import se.laz.casual.connection.caller.config.ConfigurationService;
import se.laz.casual.connection.caller.util.ConnectionFactoryFinder;
import se.laz.casual.jca.ConnectionObserver;
import se.laz.casual.jca.DomainId;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class ConnectionFactoryEntryStore implements ConnectionObserver
{
    private static final Logger LOG = Logger.getLogger(ConnectionFactoryEntryStore.class.getName());
    private final ConnectionFactoryFinder connectionFactoryFinder;
    private final TopologyChangedHandler topologyChangedHandler;
    private List<ConnectionFactoryEntry> connectionFactories;
    private ConnectionObserverHandler connectionObserverHandler;

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
        return Collections.unmodifiableList(connectionFactories);
    }

    @PostConstruct
    public synchronized void initialize()
    {
        connectionFactories = connectionFactoryFinder.findConnectionFactory(getJndiRoot());
        topologyChangedHandler.setSupplier(this::get);
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
