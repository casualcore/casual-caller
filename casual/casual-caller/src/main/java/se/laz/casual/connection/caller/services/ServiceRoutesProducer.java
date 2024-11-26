/*
 * Copyright (c) 2021 - 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.services;

import jakarta.enterprise.inject.Produces;
import se.laz.casual.connection.caller.config.ConfigurationService;

public class ServiceRoutesProducer
{
    @Produces
    public ServiceRoutes get()
    {
        return ServiceRoutes.of(ConfigurationService.getInstance().getConfiguration());
    }
}
