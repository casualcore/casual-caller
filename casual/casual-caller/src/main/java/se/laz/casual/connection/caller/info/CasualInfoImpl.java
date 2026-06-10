/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller.info;

import jakarta.ejb.Remote;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import se.laz.casual.connection.caller.ConnectionFactoryLookup;

@Remote(CasualInfo.class)
@Stateless
public class CasualInfoImpl implements CasualInfo {

    ConnectionFactoryLookup connectionFactoryLookup;

    @Inject
    public CasualInfoImpl(ConnectionFactoryLookup connectionFactoryLookup) {
        this.connectionFactoryLookup = connectionFactoryLookup;
    }

    @Override
    public void discoverService(String serviceName) {
        // Trigger discovery on service (save connections to cache)
        connectionFactoryLookup.get(serviceName);
    }
}
