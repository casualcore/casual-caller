/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import se.laz.casual.jca.CasualConnection;
import se.laz.casual.jca.DomainId;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DomainIdChecker
{
    private static final Logger LOG = Logger.getLogger(DomainIdChecker.class.getName());
    private DomainIdChecker()
    {}

    public static boolean isSameDomain(DomainId domainId, ConnectionFactoryEntry connectionFactoryEntry)
    {
        try(CasualConnection casualConnection = connectionFactoryEntry.getConnectionFactory().getConnection())
        {
            if(domainId == casualConnection.getDomainId())
            {
                return true;
            }
        }
        catch(Exception e)
        {
            LOG.log(Level.WARNING, e, () -> "failed comparing domain id: " + domainId + " - most likely the connection is gone");
        }
        return false;
    }
}
