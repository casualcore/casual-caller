package se.laz.casual.connection.caller;

import jakarta.inject.Inject;
import jakarta.resource.ResourceException;
import se.laz.casual.api.discovery.DiscoveryReturn;
import se.laz.casual.jca.CasualConnection;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class CacheRepopulator
{
    private static final Logger LOG = Logger.getLogger(CacheRepopulator.class.getName());
    private Cache cache;
    // WLS - no arg constructor
    public CacheRepopulator()
    {}

    @Inject
    public CacheRepopulator(Cache cache)
    {
        this.cache = cache;
    }

    public void repopulate(ConnectionFactoryEntry connectionFactoryEntry)
    {
        Map<CacheType, List<String>> cachedItems = cache.getAll();
        cache.purge(connectionFactoryEntry);
        Optional<DiscoveryReturn> maybeDiscoveryReturn = issueDiscovery(cachedItems, connectionFactoryEntry);
        maybeDiscoveryReturn.ifPresent(discoveryReturn ->  cache.repopulate(discoveryReturn, connectionFactoryEntry));
    }

    private Optional<DiscoveryReturn> issueDiscovery(Map<CacheType, List<String>> cachedItems, ConnectionFactoryEntry connectionFactoryEntry)
    {
        try(CasualConnection connection = connectionFactoryEntry.getConnectionFactory().getConnection())
        {
            LOG.finest(() -> "domain discovery for all known services/queues will be issued for " + connectionFactoryEntry );
            LOG.finest(() -> "all known services/queues being, services: " + cachedItems.get(CacheType.SERVICE) + " queues: " + cachedItems.get(CacheType.QUEUE));
            DiscoveryReturn discoveryReturn = connection.discover(UUID.randomUUID(),
                    cachedItems.get(CacheType.SERVICE),
                    cachedItems.get(CacheType.QUEUE));
            LOG.finest(() -> "discovery returned: " + discoveryReturn);
            return Optional.of(discoveryReturn);
        }
        catch (ResourceException e)
        {
            connectionFactoryEntry.invalidate();
            LOG.warning(() -> "failed domain discovery for: " + connectionFactoryEntry + " -> " + e);
            LOG.warning(() -> "services: " + cachedItems.get(CacheType.SERVICE) + " queues: " + cachedItems.get(CacheType.QUEUE));
        }
        return Optional.empty();
    }

}
