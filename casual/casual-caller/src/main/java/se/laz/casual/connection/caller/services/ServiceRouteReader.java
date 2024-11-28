/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.services;

import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.FileReader;

public final class ServiceRouteReader
{
    private ServiceRouteReader()
    {}
    public static ServiceRoutes load(String filename)
    {
        try
        {
            Gson gson = new Gson();
            return gson.fromJson(new FileReader(filename), ServiceRoutes.class);
        }
        catch (FileNotFoundException e)
        {
            throw new ServiceRouteReaderException("service routes file " + filename + " could not be loaded", e);
        }
    }
}
