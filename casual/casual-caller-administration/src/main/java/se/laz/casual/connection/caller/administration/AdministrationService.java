/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller.administration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.resource.ResourceException;
import se.laz.casual.api.queue.QueueInfo;
import se.laz.casual.connection.caller.*;
import se.laz.casual.connection.caller.administration.model.*;
import se.laz.casual.connection.caller.config.Configuration;
import se.laz.casual.connection.caller.config.ConfigurationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class AdministrationService
{
    private static final Logger LOG = Logger.getLogger(AdministrationService.class.getName());
    private ConnectionFactoryEntryStore connectionFactoryEntryStore;
    private Cache cache;
    private ConnectionFactoryLookup connectionFactoryLookup;
    private TransactionLess transactionLess;

    protected AdministrationService() {
        // CDI
    }

    @Inject
    public AdministrationService(ConnectionFactoryEntryStore connectionFactoryEntryStore, Cache cache, ConnectionFactoryLookup connectionFactoryLookup,
                                 TransactionLess transactionLess) {
        this.connectionFactoryEntryStore = connectionFactoryEntryStore;
        this.cache = cache;
        this.connectionFactoryLookup = connectionFactoryLookup;
        this.transactionLess = transactionLess;
    }

    /**
     * Get all configuration for casual-caller
     * @return {@link se.laz.casual.connection.caller.administration.model.Configuration}
     */
    public se.laz.casual.connection.caller.administration.model.Configuration getConfiguration()
    {
        Configuration configuration = ConfigurationService.getInstance().getConfiguration();
         return new se.laz.casual.connection.caller.administration.model.Configuration(
                 configuration.getJndiSearchRoot(),
                 configuration.getValidationIntervalMillis(),
                 configuration.isTransactionStickyEnabled(),
                 configuration.getTopologyChangeDelayMillis(),
                 configuration.getRouteFileName().orElse(null));
    }

    /**
     * Return list of ConnectionFactories
     * @return List of connection factories
     */
    public List<ServiceConnection> getConnections() {
        List<ServiceConnection> serviceConnections = new ArrayList<>();
        connectionFactoryEntryStore.get().forEach(connectionFactoryEntry -> serviceConnections
                .add(new ServiceConnection(connectionFactoryEntry.getJndiName(), connectionFactoryEntry.isValid())));
        return serviceConnections;
    }

    /**
     * Get all cached service names
     * @return List of service names
     */
    public List<String> getServices() {
        return cache.getServices();
    }

    /**
     * Get information about a specific service in form of a list with service information.
     * Services can exist in multiple locations.
     * @param serviceName - Name of service
     * @return List of service information
     */
    public ServiceInfo getService(String serviceName) {
        List<ServiceConnection> checkedConenctionFactories = new ArrayList<>();
        List<Service> services = new ArrayList<>();
        connectionFactoryLookup.get(serviceName).forEach(connectionFactoryEntry -> {
            try {
                ServiceConnection serviceConnection = new ServiceConnection(connectionFactoryEntry.getJndiName(), connectionFactoryEntry.isValid());
                transactionLess.serviceDetails(connectionFactoryEntry, con -> con.serviceDetails(serviceName))
                        .forEach(serviceDetails -> services.add(
                            new Service(serviceDetails.getName(), serviceDetails.getCategory(),
                                    serviceDetails.getTransactionType(), serviceDetails.getTimeout(),
                                    serviceDetails.getHops(), serviceConnection)));
                checkedConenctionFactories.add(serviceConnection);
            } catch (ResourceException e) {
                LOG.log(Level.WARNING, "Could not get service details for service " + serviceName, e);
            }
        });
        return new ServiceInfo(services, checkedConenctionFactories);
    }

    /**
     * Get cached queue names
     * @return List of queue names
     */
    public List<String> getQueues() {
        return cache.getQueues();
    }

    /**
     * Get information about a specific queue in form of a list with queue information.
     * Queues can exist in multiple locations.
     * @param queueName Name of queue
     * @return List of queue information
     */
    public Queue getQueue(String queueName) {
        Optional<ConnectionFactoryEntry> connectionFactoryEntry1 = connectionFactoryLookup.get(QueueInfo.of(queueName));
        ConnectionFactoryEntry connectionFactoryEntry = connectionFactoryEntry1.orElse(null);
        if (connectionFactoryEntry != null) {
            return new Queue(queueName, new QueueConnection(connectionFactoryEntry.getJndiName(), connectionFactoryEntry.isValid()));
        }
        return new Queue(queueName, null);
    }
}
