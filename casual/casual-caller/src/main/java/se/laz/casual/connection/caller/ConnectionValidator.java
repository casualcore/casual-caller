/*
 * Copyright (c) 2023 The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import jakarta.inject.Inject;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionValidator
{
    private static final Logger LOG = Logger.getLogger(ConnectionValidator.class.getName());
    private CacheRepopulator repopulator;
    private ConnectionFactoryEntryStore connectionFactoryEntryStore;
    private Cache cache;

    // WLS - no arg constructor
    public ConnectionValidator()
    {}

    @Inject
    public ConnectionValidator(CacheRepopulator repopulator, ConnectionFactoryEntryStore connectionFactoryEntryStore, Cache cache)
    {
        this.repopulator = repopulator;
        this.connectionFactoryEntryStore = connectionFactoryEntryStore;
        this.cache = cache;
    }

    public void validateAllConnections()
    {
        refreshReverseEntries();
        connectionFactoryEntryStore.get()
                                   .forEach( connectionFactoryEntry -> {
                                       try
                                       {
                                           validate(connectionFactoryEntry);
                                       }
                                       catch(Exception e)
                                       {
                                           connectionFactoryEntry.invalidate();
                                           LOG.log(Level.WARNING, e, () -> "Failed validating: " + connectionFactoryEntry);
                                       }
                                   });
    }

    private void refreshReverseEntries()
    {
        ReverseRefreshResult result = connectionFactoryEntryStore.refreshReverseEntries();
        result.purged().forEach(cache::purge);
        result.added().forEach(entry -> {
            repopulator.repopulate(entry);
            connectionFactoryEntryStore.addConnectionObserver(entry);
        });
    }

    private void validate(final ConnectionFactoryEntry connectionFactoryEntry)
    {
        boolean invalidBeforeValidation = !connectionFactoryEntry.isValid();
        connectionFactoryEntry.validate();
        if(connectionReestablished(invalidBeforeValidation, connectionFactoryEntry.isValid()))
        {
            repopulator.repopulate(connectionFactoryEntry);
            connectionFactoryEntryStore.addConnectionObserver(connectionFactoryEntry);
        }
    }

    private boolean connectionReestablished(boolean invalidBeforeRevalidation, boolean valid)
    {
        return invalidBeforeRevalidation && valid;
    }

}
