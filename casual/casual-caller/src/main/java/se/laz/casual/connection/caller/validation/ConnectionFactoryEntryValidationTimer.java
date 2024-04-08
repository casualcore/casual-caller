/*
 * Copyright (c) 2021 - 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller.validation;


import se.laz.casual.connection.caller.ConnectionValidator;
import se.laz.casual.connection.caller.config.ConfigurationService;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import java.util.logging.Logger;

@Singleton
@Startup
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ConnectionFactoryEntryValidationTimer
{
    private static final Logger LOG = Logger.getLogger(ConnectionFactoryEntryValidationTimer.class.getName());

    @Resource
    private TimerService timerService;
    private ConnectionValidator connectionValidator;
    private TimerConfig config;
    private long interval;

    public ConnectionFactoryEntryValidationTimer()
    {}

    @Inject
    public ConnectionFactoryEntryValidationTimer(ConnectionValidator connectionValidator)
    {
        this.connectionValidator = connectionValidator;
    }

    @PostConstruct
    private void setup()
    {
        interval = ConfigurationService.getInstance().getConfiguration().getValidationIntervalMillis();
        // Setup timer
        config = new TimerConfig();
        config.setPersistent(false);
        timerService.createSingleActionTimer(interval, config);
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
        finally
        {
            // To avoid overlapping timeouts, we reschedule only when work is done
            // This since, for instance on wildfly, it checks if the timeout is currently running and logs
            // a warning if that is the case - before actually calling the timeout method
            // If it did not do that, we could have made another work around to get rid of that
            timerService.createSingleActionTimer(interval, config);
        }
    }
}
