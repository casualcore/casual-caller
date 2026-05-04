/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import se.laz.casual.jca.CasualConnectionFactory;

public interface ConnectionFactoryProducer
{
    String getUniqueName();
    CasualConnectionFactory getConnectionFactory();
}
