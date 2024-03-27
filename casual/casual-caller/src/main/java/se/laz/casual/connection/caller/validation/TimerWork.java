/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.validation;

import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;
import se.laz.casual.connection.caller.ConnectionValidator;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

@Singleton
public class TimerWork
{
    private static final Logger LOG = Logger.getLogger(TimerWork.class.getName());
    private final AtomicBoolean executionInProgress = new AtomicBoolean(false);
    private ConnectionValidator connectionValidator;

    // NOP constructor needed
    public TimerWork()
    {}

    @Inject
    public TimerWork(ConnectionValidator connectionValidator)
    {
        this.connectionValidator = connectionValidator;
    }

    @Lock(LockType.READ)
    public void work()
    {
        if (!executionInProgress.compareAndSet(false, true))
        {
            LOG.finest(() -> "Execution already in progress");
            return;
        }
        try
        {
            connectionValidator.validateAllConnections();
        }
        finally
        {
            executionInProgress.set(false);
        }
    }

}
