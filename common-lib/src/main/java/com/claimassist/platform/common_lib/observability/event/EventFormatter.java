package com.claimassist.platform.common_lib.observability.event;

import com.claimassist.platform.common_lib.observability.LoggingHelper;
import org.slf4j.MDC;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Formats an EventLog into a compact JSON string suitable for structured
 * logging. This keeps formatting centralized so different event types do not
 * duplicate serialization logic.
 */
public final class EventFormatter {
    private EventFormatter() {}

    private static final DateTimeFormatter DF = DateTimeFormatter.ISO_INSTANT;

    public static String format(EventLog e) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendField(sb, "eventType", e.getEventType().name());
        appendField(sb, "service", e.getService());
        appendField(sb, "application", e.getApplication());
        appendField(sb, "correlationId", valueOrMdc(e.getCorrelationId(), "correlationId"));
        appendField(sb, "traceId", valueOrMdc(e.getTraceId(), "traceId"));
        appendField(sb, "spanId", valueOrMdc(e.getSpanId(), "spanId"));
        appendField(sb, "timestamp", DF.format(e.getTimestamp()));
        if (e.getDurationMs() != null) appendFieldRaw(sb, "duration", e.getDurationMs().toString());
        appendField(sb, "logger", e.getLogger());

        if (e.getDetails() != null && !e.getDetails().isEmpty()) {
            sb.append(',');
            sb.append("\"details\":{");
            boolean first = true;
            for (Map.Entry<String,Object> entry : e.getDetails().entrySet()) {
                if (!first) sb.append(',');
                first = false;
                appendRawJsonString(sb, entry.getKey());
                sb.append(':');
                Object v = entry.getValue();
                if (v == null) sb.append("null");
                else if (v instanceof Number) sb.append(v.toString());
                else appendRawJsonString(sb, v.toString());
            }
            sb.append('}');
        }

        sb.append('}');
        return sb.toString();
    }

    private static String valueOrMdc(String value, String mdcKey) {
        if (value != null && !value.isBlank()) return value;
        String m = MDC.get(mdcKey);
        return m == null ? "" : m;
    }

    private static void appendField(StringBuilder sb, String key, String value) {
        appendFieldRaw(sb, key, toJsonString(value));
    }

    private static void appendFieldRaw(StringBuilder sb, String key, String rawValue) {
        if (sb.length() > 1) sb.append(',');
        appendRawJsonString(sb, key);
        sb.append(':');
        sb.append(rawValue);
    }

    private static String toJsonString(String s) {
        if (s == null) return "\"\"";
        return '"' + LoggingHelper.escapeJson(s) + '"';
    }

    private static void appendRawJsonString(StringBuilder sb, String s) {
        sb.append('"');
        sb.append(LoggingHelper.escapeJson(s == null ? "" : s));
        sb.append('"');
    }
}

