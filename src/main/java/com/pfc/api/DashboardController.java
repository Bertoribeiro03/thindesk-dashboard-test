package com.pfc.api;

import com.pfc.config.ThindeskProperties;
import com.pfc.data.DashboardRepository;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.math.BigInteger;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {
    private final DashboardRepository repository;
    private final ThindeskProperties properties;
    public DashboardController(DashboardRepository repository, ThindeskProperties properties) { this.repository = repository; this.properties = properties; }
    @GetMapping("/health") public Map<String, Object> health() {
        repository.health(); return Map.of("status", "ok", "database", "ok", "data_source", properties.dataSource());
    }
    @GetMapping("/meta/filters") public Map<String, Object> metadata() { return Map.of("data", repository.metadata()); }
    @GetMapping("/dashboard/summary") public Map<String, Object> summary(@RequestParam MultiValueMap<String, String> parameters) {
        var filters = Filters.from(parameters); return response(filters, repository.summary(filters));
    }
    @GetMapping("/dashboard/timeseries") public Map<String, Object> timeseries(@RequestParam MultiValueMap<String, String> parameters) {
        var filters = Filters.from(parameters); String granularity = Optional.ofNullable(parameters.getFirst("granularity")).orElse("day");
        var result = response(filters, repository.timeseries(filters, granularity)); result.put("granularity", granularity); return result;
    }
    @GetMapping("/dashboard/status-distribution") public Map<String, Object> distribution(@RequestParam MultiValueMap<String, String> parameters) {
        var filters = Filters.from(parameters); return response(filters, repository.distribution(filters));
    }
    @GetMapping("/dashboard/top-types") public Map<String, Object> topTypes(@RequestParam MultiValueMap<String, String> parameters) {
        var filters = Filters.from(parameters); return response(filters, repository.topTypes(filters, integer(parameters, "limit", 5, 1, 50)));
    }
    @GetMapping("/tickets") public Map<String, Object> tickets(@RequestParam MultiValueMap<String, String> parameters) {
        var filters = Filters.from(parameters);
        BigInteger page;
        try {
            String value=Optional.ofNullable(parameters.getFirst("page")).orElse("1");
            if (!value.matches("[+]?[0-9]+") || value.length()>1000) throw new NumberFormatException();
            page = new BigInteger(value); if (page.signum() <= 0) throw new NumberFormatException();
        }
        catch (NumberFormatException exception) { throw ApiError.validation("page", "Informe um inteiro maior ou igual a 1."); }
        var result = new LinkedHashMap<String, Object>(repository.tickets(filters, page, integer(parameters, "page_size", 10, 1, 100)));
        result.put("filters", filters.asMap()); return result;
    }
    private static int integer(MultiValueMap<String, String> parameters, String name, int defaultValue, int min, int max) {
        try { int value = Integer.parseInt(Optional.ofNullable(parameters.getFirst(name)).orElse("" + defaultValue)); if (value < min || value > max) throw new NumberFormatException(); return value; }
        catch (NumberFormatException exception) { throw ApiError.validation(name, "Informe um inteiro entre " + min + " e " + max + "."); }
    }
    private static Map<String, Object> response(Filters filters, Object data) {
        var result = new LinkedHashMap<String, Object>(); result.put("filters", filters.asMap()); result.put("data", data); return result;
    }
}
