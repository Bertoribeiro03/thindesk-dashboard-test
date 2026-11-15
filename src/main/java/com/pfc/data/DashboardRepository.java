package com.pfc.data;

import com.pfc.api.ApiError;
import com.pfc.api.Filters;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.*;
import java.math.BigInteger;

@Repository
public class DashboardRepository {
    private final Database database;
    public DashboardRepository(Database database) { this.database = database; }

    public void health() { database.query("SELECT 1 FROM tickets LIMIT 1", List.of()); }
    public List<Map<String, Object>> types() { return database.query("SELECT id,name FROM ticket_types ORDER BY name", List.of()); }
    public Map<String, Object> metadata() {
        return Map.of("statuses", Filters.STATUSES.entrySet().stream().map(entry -> Map.of("value", entry.getKey(), "label", entry.getValue())).toList(),
                "types", types(), "date_range", database.query("SELECT date(MIN(opened_at)) AS min,date(MAX(opened_at)) AS max FROM tickets", List.of()).getFirst());
    }
    public Map<String, Object> summary(Filters filters) {
        var where = filters.where();
        var data = database.query("""
                SELECT COUNT(*) AS total, COALESCE(SUM(status='aberto'),0) AS abertos,
                  COALESCE(SUM(status='em_andamento'),0) AS em_andamento,
                  COALESCE(SUM(status='concluido'),0) AS concluidos,
                  COALESCE(SUM(status='cancelado'),0) AS cancelados,
                  ROUND(AVG(CASE WHEN status='concluido' THEN (julianday(closed_at)-julianday(opened_at))*24 END),1) AS tempo_medio_resolucao_h
                FROM tickets t
                """ + where.sql(), where.parameters()).getFirst();
        int total = number(data, "total"), completed = number(data, "concluidos");
        data.put("backlog", number(data, "abertos") + number(data, "em_andamento"));
        data.put("taxa_resolucao_pct", total == 0 ? 0.0 : rounded(completed * 100.0 / total));
        return data;
    }
    public List<Map<String, Object>> timeseries(Filters filters, String granularity) {
        String format = switch (granularity) {
            case "day" -> "%Y-%m-%d"; case "week" -> "%Y-W%W"; case "month" -> "%Y-%m";
            default -> throw ApiError.validation("granularity", "Escolha day, week ou month.");
        };
        var opened = series(filters, format, "t.opened_at", false);
        var attended = series(filters, format, "t.closed_at", true);
        var periods = new TreeSet<String>();
        if (filters.start() != null && filters.end() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(filters.start(), filters.end());
            for (long offset = 0; offset <= days; offset++) periods.add(period(filters.start().plusDays(offset), granularity));
        } else { periods.addAll(opened.keySet()); periods.addAll(attended.keySet()); }
        return periods.stream().map(period -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", period); row.put("abertos", opened.getOrDefault(period, 0)); row.put("atendidos", attended.getOrDefault(period, 0));
            return row;
        }).toList();
    }
    private Map<String, Integer> series(Filters filters, String format, String column, boolean completed) {
        var where = filters.where(column);
        var parameters = new ArrayList<Object>(); parameters.add(format); parameters.addAll(where.parameters());
        String sql = "SELECT strftime(?," + column + ") AS period,COUNT(*) AS count FROM tickets t" + where.sql();
        if (completed) sql += (where.sql().isEmpty() ? " WHERE " : " AND ") + "t.status='concluido' AND t.closed_at IS NOT NULL";
        var result = new TreeMap<String, Integer>();
        for (var row : database.query(sql + " GROUP BY period ORDER BY period", parameters)) result.put((String) row.get("period"), number(row, "count"));
        return result;
    }
    static String period(LocalDate date, String granularity) {
        if (granularity.equals("month")) return "%04d-%02d".formatted(date.getYear(), date.getMonthValue());
        if (granularity.equals("week")) {
            int firstMonday = (8 - date.withDayOfYear(1).getDayOfWeek().getValue()) % 7;
            int day = date.getDayOfYear() - 1;
            int week = day < firstMonday ? 0 : 1 + (day - firstMonday) / 7;
            return "%04d-W%02d".formatted(date.getYear(), week);
        }
        return date.toString();
    }
    public List<Map<String, Object>> distribution(Filters filters) {
        var where = filters.where();
        var counts = new HashMap<String, Integer>();
        for (var row : database.query("SELECT status,COUNT(*) AS count FROM tickets t" + where.sql() + " GROUP BY status", where.parameters())) counts.put((String) row.get("status"), number(row, "count"));
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return Filters.STATUSES.entrySet().stream().map(entry -> {
            int count = counts.getOrDefault(entry.getKey(), 0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", entry.getKey()); row.put("label", entry.getValue()); row.put("count", count); row.put("pct", total == 0 ? 0.0 : rounded(count * 100.0 / total));
            return row;
        }).toList();
    }
    public List<Map<String, Object>> topTypes(Filters filters, int limit) {
        var where = filters.where();
        int total = number(database.query("SELECT COUNT(*) AS n FROM tickets t" + where.sql(), where.parameters()).getFirst(), "n");
        var parameters = new ArrayList<>(where.parameters()); parameters.add(limit);
        var rows = database.query("SELECT tt.id AS type_id,tt.name AS type,COUNT(*) AS count FROM tickets t JOIN ticket_types tt ON tt.id=t.type_id"
                + where.sql() + " GROUP BY tt.id,tt.name ORDER BY count DESC,tt.name ASC LIMIT ?", parameters);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i); row.put("rank", i + 1); row.put("pct", total == 0 ? 0.0 : rounded(number(row, "count") * 100.0 / total));
        }
        return rows;
    }
    public Map<String, Object> tickets(Filters filters, BigInteger page, int pageSize) {
        var where = filters.where();
        return database.read(connection -> {
            connection.setAutoCommit(false);
            int total = number(Database.query(connection, "SELECT COUNT(*) AS n FROM tickets t" + where.sql(), where.parameters()).getFirst(), "n");
            var parameters = new ArrayList<>(where.parameters()); parameters.add(pageSize);
            List<Map<String, Object>> rows = List.of();
            BigInteger offset = page.subtract(BigInteger.ONE).multiply(BigInteger.valueOf(pageSize));
            if (offset.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
                parameters.add(offset.longValue());
                rows = Database.query(connection, "SELECT t.id,t.title,tt.name AS type,t.status,t.priority,t.technician,t.technical_notes,t.opened_at,t.closed_at FROM tickets t JOIN ticket_types tt ON tt.id=t.type_id"
                        + where.sql() + " ORDER BY t.opened_at DESC,t.id DESC LIMIT ? OFFSET ?", parameters);
            }
            return Map.of("items", rows, "page", page, "page_size", pageSize, "total", total, "pages", (total + pageSize - 1) / pageSize);
        });
    }
    private static int number(Map<String, Object> row, String key) { return ((Number) row.get(key)).intValue(); }
    private static double rounded(double value) { return Math.rint(value * 10) / 10; }
}
