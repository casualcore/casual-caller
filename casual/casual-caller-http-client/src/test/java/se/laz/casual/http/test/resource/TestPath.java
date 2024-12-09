/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.http.test.resource;

import java.util.Arrays;

public enum TestPath
{
    OK("ok"),
    NOT_FOUND("notfound"),
    ERROR("error"),
    ERROR_WITH_BODY("errorWithBody"),
    TIMEOUT("timeout"),
    TIMEOUT_WITH_BODY("timeoutWithBody");
    private final String path;

    TestPath(String path)
    {
        this.path = path;
    }

    public String getPath()
    {
        return path;
    }

    public static TestPath unmarshall(String path)
    {
        return Arrays.stream(values())
                     .filter(v -> v.getPath().equals(path))
                     .findFirst()
                     .orElse(NOT_FOUND);
    }
}
