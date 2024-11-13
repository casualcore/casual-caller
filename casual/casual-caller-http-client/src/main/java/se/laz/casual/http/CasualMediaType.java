/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.http;

import jakarta.ws.rs.core.MediaType;

public class CasualMediaType
{
    public static final MediaType FIELDED = new MediaType("application", "casual-field");
    public static final MediaType X_OCTET = new MediaType("application", "casual-x-octet");
    public static final MediaType C_STRING = new MediaType("application", "casual-string");
    public static final MediaType CASUAL_NULL = new MediaType("application", "casual-null");
}
