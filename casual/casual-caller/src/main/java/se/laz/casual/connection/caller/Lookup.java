/*
 * Copyright (c) 2017 - 2021, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import se.laz.casual.api.queue.QueueInfo;
import se.laz.casual.api.service.ServiceDetails;
import se.laz.casual.jca.CasualConnection;

import jakarta.resource.ResourceException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Lookup
{
    private static final Logger LOG = Logger.getLogger(Lookup.class.getName());

    public List<ConnectionFactoryEntry> find(QueueInfo qinfo, List<ConnectionFactoryEntry> cacheEntries, TransactionLess transactionLess)
    {
        return findQueue(cacheEntries, con -> con.queueExists(qinfo), transactionLess);
    }

    public ConnectionFactoriesByPriority find(String serviceName, List<ConnectionFactoryEntry> cacheEntries, TransactionLess transactionLess)
    {
        return findService(cacheEntries, con -> con.serviceDetails(serviceName), transactionLess);
    }

    private ConnectionFactoriesByPriority findService(List<ConnectionFactoryEntry> cacheEntries,
                                                      Function<CasualConnection, List<ServiceDetails>> fetchFunction, TransactionLess transactionLess)
    {
        ConnectionFactoriesByPriority foundEntries = ConnectionFactoriesByPriority.emptyInstance();
        for (ConnectionFactoryEntry entry : cacheEntries)
        {
            if (!foundEntries.isResolved(entry.getJndiName()))
            {
                try
                {
                   foundEntries.store(transactionLess.serviceDetails(entry, fetchFunction), entry);
                   foundEntries.setResolved(entry.getJndiName());
                }
                catch (ResourceException e)
                {
                   LOG.log(Level.WARNING, e, ()->"Skipping connection factory " + entry.getJndiName() + " for service lookup, received error: " + e.getMessage());
                }
            }
        }
        return foundEntries;
    }

    private List<ConnectionFactoryEntry> findQueue(List<ConnectionFactoryEntry> cacheEntries, PredicateThrowsResourceException<CasualConnection> predicate, TransactionLess transactionLess)
    {
        List<ConnectionFactoryEntry> foundEntries = new ArrayList<>();
        for (ConnectionFactoryEntry entry : cacheEntries)
        {
            try
            {
                if (transactionLess.queueExists(entry, predicate))
                {
                    foundEntries.add(entry);
                }
            }
            catch (ResourceException e)
            {
                LOG.log(Level.WARNING, e, ()->"Skipping connection factory " + entry.getJndiName() + " for queue lookup on, received error: " + e.getMessage());
            }
        }
        return foundEntries;
    }

    public interface PredicateThrowsResourceException<T>
    {
        boolean test(T object) throws ResourceException;
    }
}
