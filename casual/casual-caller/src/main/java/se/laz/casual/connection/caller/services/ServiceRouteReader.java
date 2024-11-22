/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.services;

import se.laz.casual.api.external.json.JsonProviderFactory;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ServiceRouteReader
{
    private static final Logger LOG = Logger.getLogger(ServiceRouteReader.class.getName());
    private ServiceRouteReader()
    {}
    public static ServiceRoutes load(String filename)
    {
        try
        {
            return JsonProviderFactory.getJsonProvider().fromJson(new FileReader(filename), ServiceRoutes.class);
        }
        catch (FileNotFoundException e)
        {
            LOG.log(Level.WARNING, e, () -> "service routes file " + filename + " could not be loaded. http service routing will not work");
            return ServiceRoutes.EMPTY;
        }
    }
}
