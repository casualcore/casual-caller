/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.services;

import se.laz.casual.connection.caller.config.Configuration;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ServiceRoutes
{
    private static final ServiceRoutes EMPTY = new ServiceRoutes(Collections.emptySet());
    private final Set<Route> routes;
    private ServiceRoutes(Set<Route> routes)
    {
        this.routes = routes;
    }
    public static ServiceRoutes of(Configuration config)
    {
        ServiceRoutes serviceRoutes =  config.getRouteFileName()
                                             .map(ServiceRoutes::toPath)
                                             .map(ServiceRouteReader::load)
                                             .orElse(EMPTY);
        serviceRoutes.pruneNullRoutes();
        return serviceRoutes;
    }

    private static Path toPath(String s)
    {
        // If 's' is absolute, toAbsolutePath() returns 's' unchanged.
        // If 's' is relative, it resolves against the CWD automatically.
        return Path.of(s).toAbsolutePath().normalize();
    }

    public boolean isEmpty()
    {
        return routes.isEmpty();
    }

    public Optional<URI> getRoute(String service)
    {
        return routes.stream()
                     .filter(route -> route.name().equals(service))
                     .map(Route::uri)
                     .findFirst();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof ServiceRoutes that))
        {
            return false;
        }
        return Objects.equals(routes, that.routes);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(routes);
    }

    @Override
    public String toString()
    {
        return "ServiceRoutes{" +
                "routes=" + routes +
                '}';
    }

    // note: in case gson was used, it is lenient towards trailing comma leading to potential null values
    // we do not want accidental null values
    private void pruneNullRoutes()
    {
        routes.removeIf(Objects::isNull);
    }
}
