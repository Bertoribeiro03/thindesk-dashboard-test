package com.pfc.api;

import java.util.LinkedHashMap;
import java.util.Map;

public class ApiError extends RuntimeException {
    private final int status;
    private final String code;
    private final Object details;

    public ApiError(int status, String code, String message) { this(status, code, message, null); }
    public ApiError(int status, String code, String message, Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }
    public int status() { return status; }
    public Object details() { return details; }
    public Map<String, Object> body() {
        var error = new LinkedHashMap<String, Object>();
        error.put("code", code);
        error.put("message", getMessage());
        error.put("details", details);
        return Map.of("error", error);
    }
    public static ApiError validation(String param, String message) {
        return new ApiError(400, "VALIDATION_ERROR", "Parâmetros inválidos.",
                java.util.List.of(Map.of("param", param, "msg", message)));
    }
}
