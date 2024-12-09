/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.http;

import jakarta.ws.rs.core.MediaType;

public class CasualMediaType
{
    private static final String MAIN_TYPE = "application";
    private CasualMediaType()
    {}
    public static final MediaType FIELDED = new MediaType(MAIN_TYPE, "casual-field");
    public static final MediaType X_OCTET = new MediaType(MAIN_TYPE, "casual-x-octet");
    public static final MediaType C_STRING = new MediaType(MAIN_TYPE, "casual-string");
    public static final MediaType CASUAL_NULL = new MediaType(MAIN_TYPE, "casual-null");
}
