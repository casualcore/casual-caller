/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.http;

import jakarta.ws.rs.core.MediaType;

import static se.laz.casual.http.CasualMediaType.CASUAL_NULL;
import static se.laz.casual.http.CasualMediaType.C_STRING;
import static se.laz.casual.http.CasualMediaType.FIELDED;
import static se.laz.casual.http.CasualMediaType.X_OCTET;

public class CasualContentTypeConverter
{
    private CasualContentTypeConverter()
    {}
    public static MediaType convert(CasualContentType contentType)
    {
        return switch(contentType)
        {
            case X_OCTET -> MediaType.APPLICATION_OCTET_STREAM_TYPE;
            case JSON -> MediaType.APPLICATION_JSON_TYPE;
            case FIELD -> FIELDED;
            case OCTET -> X_OCTET;
            case STRING -> C_STRING;
            case NULL -> CASUAL_NULL;
        };
    }
}
