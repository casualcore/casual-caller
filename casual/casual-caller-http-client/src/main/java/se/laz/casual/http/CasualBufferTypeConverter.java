/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.http;

import jakarta.ws.rs.core.MediaType;
import se.laz.casual.api.buffer.CasualBufferType;

import static se.laz.casual.http.CasualMediaType.FIELDED;
import static se.laz.casual.http.CasualMediaType.X_OCTET;
import static se.laz.casual.http.CasualMediaType.C_STRING;

public final class CasualBufferTypeConverter
{
    private CasualBufferTypeConverter()
    {
    }
    public static MediaType convert(CasualBufferType bufferType)
    {
        return switch (bufferType)
        {
            case FIELDED -> FIELDED;
            case CSTRING -> C_STRING;
            case JSON, JSON_JSCD -> MediaType.APPLICATION_JSON_TYPE;
            case X_OCTET -> X_OCTET;
        };
    }
}
