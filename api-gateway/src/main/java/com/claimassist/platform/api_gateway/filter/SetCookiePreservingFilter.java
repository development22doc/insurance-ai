package com.claimassist.platform.api_gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.cloud.gateway.filter.GlobalFilter;

import java.util.List;

/**
 * Ensures multiple Set-Cookie headers emitted by downstream services are delivered
 * to the browser as separate Set-Cookie header lines. Some proxies or header
 * manipulations may accidentally collapse them into a single comma-separated
 * header value which browsers do not treat equivalently.
 *
 * This filter runs after the request has been proxied and inspects the response
 * headers. If it finds a single Set-Cookie header that contains both token
 * cookies separated by a comma, it will split them back into independent headers.
 *
 * It is conservative: it only acts when there is exactly one Set-Cookie header
 * value and that value appears to contain multiple token cookies. It will not
 * duplicate headers if the gateway already preserved them correctly.
 */
@Component
public class SetCookiePreservingFilter implements GlobalFilter, Ordered {

    private static final String ACCESS_COOKIE = "CLAIMASSIST_ACCESS_TOKEN=";
    private static final String REFRESH_COOKIE = "CLAIMASSIST_REFRESH_TOKEN=";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        // Register a beforeCommit callback so we mutate headers before the response is committed.
        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            HttpHeaders headers = response.getHeaders();

            List<String> setCookieValues = headers.get(HttpHeaders.SET_COOKIE);
            if (setCookieValues == null) {
                return Mono.empty();
            }

            // If there are already multiple Set-Cookie header values, assume gateway preserved them
            if (setCookieValues.size() > 1) {
                return Mono.empty();
            }

            String single = setCookieValues.size() == 1 ? setCookieValues.get(0) : null;
            if (single == null) {
                return Mono.empty();
            }

            // Only attempt splitting when it looks like both cookies are present and there's at least one comma.
            if (single.contains(ACCESS_COOKIE) && single.contains(REFRESH_COOKIE) && single.contains(",")) {
                List<String> parts = splitTopLevelSetCookie(single);

                // Ensure we actually produced multiple top-level cookie strings and that the token cookies
                // are present in the resulting parts. This avoids accidental splitting of unrelated headers.
                if (parts.size() > 1) {
                    boolean hasAccess = parts.stream().anyMatch(p -> p.contains(ACCESS_COOKIE));
                    boolean hasRefresh = parts.stream().anyMatch(p -> p.contains(REFRESH_COOKIE));

                    if (hasAccess && hasRefresh) {
                        headers.remove(HttpHeaders.SET_COOKIE);
                        for (String part : parts) {
                            if (part != null && !part.isBlank()) {
                                headers.add(HttpHeaders.SET_COOKIE, part);
                            }
                        }
                    }
                }
            }

            return Mono.empty();
        });

        // Continue filter chain. The beforeCommit callback will run if/when the response is committed by downstream.
        return chain.filter(exchange);
    }

    /**
     * Split a single combined Set-Cookie header into top-level cookie strings while
     * ignoring commas inside an Expires attribute value. This is conservative and
     * only treats commas outside of Expires=...; as separators.
     */
    private static List<String> splitTopLevelSetCookie(String header) {
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inExpires = false;
        int len = header.length();

        for (int i = 0; i < len; i++) {
            char c = header.charAt(i);

            // Detect start of Expires= (case-insensitive)
            if (!inExpires && (c == 'E' || c == 'e') && i + 8 <= len && header.regionMatches(true, i, "Expires=", 0, 8)) {
                inExpires = true;
            }

            if (c == ';') {
                // Semicolon terminates attribute values (including Expires)
                inExpires = false;
            }

            if (c == ',') {
                if (!inExpires) {
                    // Top-level delimiter between cookie strings
                    String part = cur.toString().trim();
                    if (!part.isBlank()) {
                        parts.add(part);
                    }
                    cur.setLength(0);
                } else {
                    // We're inside an Expires value. A comma here could be the date comma (Wed, 09...) or
                    // the cookie-level separator that follows the Expires value when Expires is the last
                    // attribute (no trailing semicolon). Look ahead: if the characters after the comma
                    // (skipping spaces) start with one of our known cookie names, treat this as a separator.
                    int j = i + 1;
                    while (j < len && Character.isWhitespace(header.charAt(j))) j++;

                    if (j < len && (header.regionMatches(true, j, ACCESS_COOKIE, 0, ACCESS_COOKIE.length())
                            || header.regionMatches(true, j, REFRESH_COOKIE, 0, REFRESH_COOKIE.length()))) {
                        String part = cur.toString().trim();
                        if (!part.isBlank()) {
                            parts.add(part);
                        }
                        cur.setLength(0);
                        inExpires = false; // end of expires/value
                    } else {
                        // It's a comma inside Expires value (date), keep it
                        cur.append(c);
                    }
                }
            } else {
                cur.append(c);
            }
        }

        String last = cur.toString().trim();
        if (!last.isBlank()) {
            parts.add(last);
        }

        return parts;
    }

    @Override
    public int getOrder() {
        // We must register beforeCommit before the framework's NettyWriteResponseFilter
        // commits the proxied response and copies downstream headers. Ensure our filter
        // runs just before that filter so the callback is registered in time.
        // Use the framework constant when available to position reliably.
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }
}
