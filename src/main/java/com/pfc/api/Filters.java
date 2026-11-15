package com.pfc.api;

import org.springframework.util.MultiValueMap;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public record Filters(LocalDate start, LocalDate end, List<String> statuses, List<BigInteger> typeIds) {
    public static final Map<String, String> STATUSES;
    static {
        var labels = new LinkedHashMap<String, String>();
        labels.put("aberto", "Aberto"); labels.put("em_andamento", "Em andamento");
        labels.put("concluido", "Concluído"); labels.put("cancelado", "Cancelado");
        STATUSES = Collections.unmodifiableMap(labels);
    }
    public record Where(String sql, List<Object> parameters) {}

    public static Filters from(MultiValueMap<String, String> parameters) {
        LocalDate start = date(parameters.getFirst("start"), "start");
        LocalDate end = date(parameters.getFirst("end"), "end");
        if (start != null && end != null) {
            if (start.isAfter(end)) throw new ApiError(400, "INVALID_PERIOD", "A data inicial deve ser anterior à final.");
            if (ChronoUnit.DAYS.between(start, end) > 731) throw new ApiError(400, "PERIOD_TOO_LONG", "O período não pode exceder 731 dias.");
        }
        List<String> statuses = csv(parameters.getFirst("status"));
        var invalid = statuses.stream().filter(value -> !STATUSES.containsKey(value)).toList();
        if (!invalid.isEmpty()) throw new ApiError(400, "INVALID_STATUS", "Status inválido.", Map.of("invalid", invalid, "allowed", new ArrayList<>(STATUSES.keySet())));
        var typeIds = new TreeSet<BigInteger>();
        for (String value : csv(parameters.getFirst("type_id"))) {
            try {
                if (!value.matches("[0-9]+") || value.length() > 1000) throw new NumberFormatException();
                var id = new BigInteger(value);
                if (id.signum() <= 0) throw new NumberFormatException();
                typeIds.add(id);
            } catch (NumberFormatException exception) {
                throw new ApiError(400, "INVALID_TYPE_ID", "Informe IDs de tipo inteiros positivos válidos.");
            }
        }
        return new Filters(start, end, statuses, List.copyOf(typeIds));
    }

    private static List<String> csv(String value) {
        return value == null ? List.of() : Arrays.stream(value.split(",", -1)).map(String::strip).distinct().sorted().toList();
    }
    private static LocalDate date(String value, String parameter) {
        if (value == null) return null;
        try {
            if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException();
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1) throw new IllegalArgumentException();
            return date;
        } catch (RuntimeException exception) {
            throw ApiError.validation(parameter, "Use uma data válida no formato AAAA-MM-DD.");
        }
    }

    public Where where() { return where("t.opened_at"); }
    public Where where(String dateColumn) {
        if (!Set.of("t.opened_at", "t.closed_at").contains(dateColumn)) throw new IllegalArgumentException("Coluna não permitida.");
        var clauses = new ArrayList<String>();
        var values = new ArrayList<Object>();
        if (start != null) { clauses.add(dateColumn + " >= ?"); values.add(start + " 00:00:00"); }
        if (end != null) {
            if (end.equals(LocalDate.of(9999, 12, 31))) { clauses.add(dateColumn + " <= ?"); values.add("9999-12-31 23:59:59"); }
            else { clauses.add(dateColumn + " < ?"); values.add(end.plusDays(1) + " 00:00:00"); }
        }
        if (!statuses.isEmpty()) { clauses.add("t.status IN (" + String.join(",", Collections.nCopies(statuses.size(), "?")) + ")"); values.addAll(statuses); }
        if (!typeIds.isEmpty()) { clauses.add("t.type_id IN (" + String.join(",", Collections.nCopies(typeIds.size(), "?")) + ")"); typeIds.forEach(id -> values.add(id.toString())); }
        return new Where(clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses), values);
    }

    public Map<String, Object> asMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("start", start); map.put("end", end); map.put("status", statuses); map.put("type_id", typeIds);
        return map;
    }
}
