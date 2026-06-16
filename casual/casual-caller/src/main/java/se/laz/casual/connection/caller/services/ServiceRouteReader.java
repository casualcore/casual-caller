/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.services;

import se.laz.casual.api.external.json.JsonProviderFactory;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

public final class ServiceRouteReader
{
    private ServiceRouteReader()
    {}
    public static ServiceRoutes load(Path filename)
    {
        try(FileReader fileReader = new FileReader(filename.toFile()))
        {
            return JsonProviderFactory.getJsonProvider().fromJson(fileReader, ServiceRoutes.class);
        }
        catch (IOException e)
        {
            throw new ServiceRouteReaderException("service routes file " + filename + " could not be loaded", e);
        }
    }
}
