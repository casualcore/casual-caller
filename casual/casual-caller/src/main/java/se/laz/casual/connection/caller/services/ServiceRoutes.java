/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.services;

import se.laz.casual.connection.caller.config.ConfigurationService;

import java.net.URI;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ServiceRoutes
{
    private final Set<Route> routes;
    private ServiceRoutes(Set<Route> routes)
    {
        this.routes = routes;
    }
    public static ServiceRoutes of()
    {
        ServiceRoutes serviceRoutes =  ConfigurationService.getInstance().getConfiguration().getRouteFileName()
                                                           .map(ServiceRouteReader::load)
                                                           .orElseGet(() -> new ServiceRoutes(Collections.emptySet()));
        // note: in case gson was used, it is lenient towards trailing comma leading to potential null values
        // we do not want accidental null values
        serviceRoutes.pruneNullRoutes();
        return serviceRoutes;
    }

    public boolean isEmpty()
    {
        return routes.isEmpty();
    }

    public Optional<URI> getRoute(String service)
    {
        return routes.stream()
                     .filter(route -> route.name().equals(service))
                     .map(route -> route.uri())
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

    private void pruneNullRoutes()
    {
        routes.removeIf(Objects::isNull);
    }
}
