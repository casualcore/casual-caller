package se.laz.casual.http;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ServiceMapping
{
    private final List<Route> routes;
    public ServiceMapping(List<Route> routes)
    {
        this.routes = routes;
    }
    public List<Route> getRoutes()
    {
        return Collections.unmodifiableList(routes);
    }
    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof ServiceMapping serviceMapping))
        {
            return false;
        }
        return Objects.equals(getRoutes(), serviceMapping.getRoutes());
    }
    @Override
    public int hashCode()
    {
        return Objects.hashCode(getRoutes());
    }
    @Override
    public String toString()
    {
        return "ServiceMapping{" +
                "routes=" + routes +
                '}';
    }
}
