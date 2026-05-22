package se.laz.casual.connection.caller.info;

import jakarta.ejb.Remote;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import se.laz.casual.connection.caller.Cache;
import se.laz.casual.connection.caller.ConnectionFactoriesByPriority;
import se.laz.casual.connection.caller.ConnectionFactoryLookup;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Remote(CasualInfo.class)
@Stateless
public class CasualInfoImpl implements CasualInfo
{
    private static final Logger LOG = Logger.getLogger(CasualInfoImpl.class.getName());

    Cache cache;
    ConnectionFactoryLookup connectionFactoryLookup;

    @Inject
    public CasualInfoImpl(Cache cache, ConnectionFactoryLookup connectionFactoryLookup) {
        this.cache = cache;
        this.connectionFactoryLookup = connectionFactoryLookup;
    }

    @Override
    public List<Service> getServices() {
        List<Service> services = new ArrayList<>();
        // Get all cached service names
        cache.getServices().forEach(service -> {
            // For a specific service, multiple connections can occur
            ConnectionFactoriesByPriority connectionFactoriesByPriority = cache.get(service);
            // Each connection has a priority, which also represents the number of hops to the service
            connectionFactoriesByPriority.getOrderedKeys().forEach(priority -> {
                // For each connection, add a service entry
                connectionFactoriesByPriority.getForPriority(priority).forEach(connectionFactoryEntry ->
                        services.add(new Service.Builder().name(service)
                                .hops(priority)
                                .jndiName(connectionFactoryEntry.getJndiName())
                                .valid(connectionFactoryEntry.isValid()).build()));
            });

        });
        return services;
    }

    @Override
    public void discoverService(String serviceName)
    {
        // Trigger discovery on service (save connections to cache)
        connectionFactoryLookup.get(serviceName);
    }
}
