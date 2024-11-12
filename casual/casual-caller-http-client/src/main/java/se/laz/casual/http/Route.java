package se.laz.casual.http;

import java.net.URL;
import java.util.Objects;

public record Route(String name, URL url)
{
    public Route
    {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(url, "url cannot be null");
    }
}
