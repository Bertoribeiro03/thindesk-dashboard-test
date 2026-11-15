package com.pfc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

@RestController
public class OpenApiController {
    private final ObjectMapper mapper;
    public OpenApiController(ObjectMapper mapper) { this.mapper=mapper; }
    @GetMapping("/openapi.json") public Map<String,Object> spec(HttpServletRequest request) throws IOException {
        try (var stream=new ClassPathResource("api/openapi.json").getInputStream()) {
            @SuppressWarnings("unchecked") Map<String,Object> spec=mapper.readValue(stream, LinkedHashMap.class);
            spec.put("servers", List.of(Map.of("url", request.getContextPath().isBlank() ? "/" : request.getContextPath())));
            return spec;
        }
    }
}
