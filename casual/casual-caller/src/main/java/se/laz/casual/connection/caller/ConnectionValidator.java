/*
 * Copyright (c) 2022, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import se.laz.casual.api.discovery.DiscoveryReturn;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConnectionValidator
{
    private Cache cache;
    private TransactionLess transactionLess;

    // WLS - no arg constructor
    public ConnectionValidator()
    {}

    @Inject
    public ConnectionValidator(Cache cache, TransactionLess transactionLess)
    {
        this.cache = cache;
        this.transactionLess = transactionLess;
    }

    public void validate(final ConnectionFactoryEntry connectionFactoryEntry)
    {
        boolean invalidBeforeValidation = !connectionFactoryEntry.isValid();
        connectionFactoryEntry.validate();
        if(connectionReestablished(invalidBeforeValidation, connectionFactoryEntry.isValid()))
        {
            Map<CacheType, List<String>> cachedItems = cache.getAll();
            cache.purge(connectionFactoryEntry);
            Optional<DiscoveryReturn> maybeDiscoveryReturn = transactionLess.discover(connectionFactoryEntry, cachedItems);
            maybeDiscoveryReturn.ifPresent(discoveryReturn ->  cache.repopulate(discoveryReturn, connectionFactoryEntry));
        }
    }

    private boolean connectionReestablished(boolean invalidBeforeRevalidation, boolean valid)
    {
        return invalidBeforeRevalidation && valid;
    }

}
