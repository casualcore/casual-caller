/*
 * Copyright (c) 2021, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.Timeout;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import se.laz.casual.connection.caller.config.ConfigurationService;

import java.util.logging.Logger;

@Singleton
@Startup
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ConnectionFactoryEntryValidationTimer
{
    private static final Logger LOG = Logger.getLogger(ConnectionFactoryEntryValidationTimer.class.getName());

    @Resource
    private TimerService timerService;

    @Inject
    ConnectionValidator connectionValidator;

    @PostConstruct
    private void setup()
    {
        long interval = ConfigurationService.getInstance().getConfiguration().getValidationIntervalMillis();

        // Setup timer
        TimerConfig config = new TimerConfig();
        config.setPersistent(false);
        timerService.createIntervalTimer(0, interval, config);
    }

    @Timeout
    public void validateConnectionFactories()
    {
        LOG.finest("Running ConnectionFactoryEntryValidationTimer");
        try
        {
            connectionValidator.validateAllConnections();
        }
        catch(Exception e)
        {
            LOG.warning(() -> "failed validating connection factories: " + e);
        }
    }
}
