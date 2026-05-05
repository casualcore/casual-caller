/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import jakarta.enterprise.inject.Produces;
import se.laz.casual.connection.caller.util.ConnectionFactoryFinderImpl;

public class ConnectionFactoryFinderProducer
{
    @Produces
    public ConnectionFactoryFinder produceConnectionFactoryFinder()
    {
        return ConnectionFactoryFinderImpl.of();
    }
}
