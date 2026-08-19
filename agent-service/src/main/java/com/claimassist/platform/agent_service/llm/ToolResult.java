package com.claimassist.platform.agent_service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Structured tool result wrapper returned to the LLM. The model reads these
 * reliably instead of relying on arbitrary {@code toString()} output, and
 * failures are signalled explicitly with a stable error code rather than by
 * leaking internal exceptions.
 *
 * <pre>
 * success:  {"success":true,  "data":{...},            "source":"claims-service"}
 * failure:  {"success":false, "errorCode":"...", "retryable":true,  "message":"..."}
 * </pre>
 */
public record ToolResult(
        boolean success,
        Object data,
        String source,
        String errorCode,
        boolean retryable,
        String message
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ToolResult success(Object data, String source) {
        return new ToolResult(true, data, source, null, false, null);
    }

    public static ToolResult failure(String errorCode, boolean retryable, String message) {
        return new ToolResult(false, null, null, errorCode, retryable, message);
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{\"success\":false,\"errorCode\":\"SERIALIZATION_ERROR\",\"retryable\":false," +
                    "\"message\":\"Unable to format the tool result.\"}";
        }
    }
}