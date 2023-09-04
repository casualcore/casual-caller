/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import jakarta.inject.Inject;

/*
 * Handles the domain discoveries that could not be carried out due to not being able to be scheduled
 * This is something that should rarely happen
 */
public class FailedDomainDiscoveryHandler
{
    private CacheRepopulator cacheRepopulator;
    private ConnectionFactoryEntryStore connectionFactoryProvider;

    // wls NOP-constructor
    public FailedDomainDiscoveryHandler()
    {}

    @Inject
    public FailedDomainDiscoveryHandler(CacheRepopulator cacheRepopulator, ConnectionFactoryEntryStore connectionFactoryProvider)
    {
        this.cacheRepopulator = cacheRepopulator;
        this.connectionFactoryProvider = connectionFactoryProvider;
    }

    public void issueDomainDiscoveryAndRepopulateCache()
    {
        connectionFactoryProvider.get().stream()
                                 .filter(ConnectionFactoryEntry::getNeedsDomainDiscovery)
                                 .forEach(cacheRepopulator::repopulate);
    }

}
