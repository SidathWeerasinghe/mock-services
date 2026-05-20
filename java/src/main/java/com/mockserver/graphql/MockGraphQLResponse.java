package com.mockserver.graphql;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable response POJO returned by all GraphQL Query / Mutation / Subscription resolvers.
 * Field names match 1-to-1 with the GraphQL schema type {@code MockResponse}.
 */
public class MockGraphQLResponse {

    private final String  requestId;
    private final String  method;
    private final String  resource;
    private final int     sizeKb;
    private final String  format;
    private final int     byteLength;
    private final String  timestamp;
    private final String  payload;

    public MockGraphQLResponse(String method, String resource, int sizeKb,
                               String format, String payload) {
        this.requestId  = UUID.randomUUID().toString();
        this.method     = method.toUpperCase();
        this.resource   = resource;
        this.sizeKb     = sizeKb;
        this.format     = format.toLowerCase();
        this.byteLength = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        this.timestamp  = Instant.now().toString();
        this.payload    = payload;
    }

    // ── Getters (Spring GraphQL uses getter reflection) ───────────────────────

    public String  getRequestId()  { return requestId;  }
    public String  getMethod()     { return method;     }
    public String  getResource()   { return resource;   }
    public int     getSizeKb()     { return sizeKb;     }
    public String  getFormat()     { return format;     }
    public int     getByteLength() { return byteLength; }
    public String  getTimestamp()  { return timestamp;  }
    public String  getPayload()    { return payload;    }
}
