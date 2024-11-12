package se.laz.casual.http;

import java.util.Arrays;

public enum CasualContentType
{
    OCTET("application/octet-stream"),
    X_OCTET("application/casual-x-octet"),
    JSON("application/json"),
    FIELD("application/casual-field"),
    STRING("application/casual-string"),
    NULL("application/casual-null");
    private final String contentType;
    CasualContentType(String contentType)
    {
        this.contentType = contentType;
    }
    public static CasualContentType unmarshall(String contentType)
    {
        return Arrays.stream(values())
                .filter((type) -> type.contentType.equals(contentType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported content type: " + contentType));
    }
    public String getContentType()
    {
        return contentType;
    }
}
