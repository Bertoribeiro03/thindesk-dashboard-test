package com.pfc.data;

import com.pfc.config.ThindeskProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.sql.*;
import java.util.*;

@Component
public class Database {
    private final ThindeskProperties properties;
    public Database(ThindeskProperties properties) { this.properties = properties; }

    @FunctionalInterface public interface Work<T> { T apply(Connection connection) throws SQLException; }

    public Connection open(boolean readonly) throws SQLException {
        var path = properties.databasePath();
        String url = readonly ? "jdbc:sqlite:" + path.toUri() + "?mode=ro" : "jdbc:sqlite:" + path;
        Connection connection = DriverManager.getConnection(url);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    public <T> T read(Work<T> work) {
        try (var connection = open(true)) { return work.apply(connection); }
        catch (SQLException exception) { throw unavailable(exception); }
    }

    public <T> T write(Work<T> work) {
        try (var connection = open(false)) {
            connection.setAutoCommit(false);
            try {
                T value = work.apply(connection);
                connection.commit();
                return value;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) { throw unavailable(exception); }
    }

    public List<Map<String, Object>> query(String sql, List<?> parameters) {
        return read(connection -> query(connection, sql, parameters));
    }

    public static List<Map<String, Object>> query(Connection connection, String sql, List<?> parameters) throws SQLException {
        try (var statement = prepare(connection, sql, parameters); var result = statement.executeQuery()) {
            var rows = new ArrayList<Map<String, Object>>();
            while (result.next()) {
                var row = new LinkedHashMap<String, Object>();
                for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) {
                    row.put(result.getMetaData().getColumnLabel(i), result.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        }
    }

    public static PreparedStatement prepare(Connection connection, String sql, List<?> parameters) throws SQLException {
        var statement = connection.prepareStatement(sql);
        for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
        return statement;
    }

    private DataAccessResourceFailureException unavailable(SQLException exception) {
        return new DataAccessResourceFailureException("Banco de dados indisponível.", exception);
    }

    @PostConstruct
    void initialize() throws Exception {
        if (!"local_sqlite".equals(properties.dataSource())) {
            throw new IllegalStateException("Fonte não configurada. Use THINDESK_DATA_SOURCE=local_sqlite.");
        }
        if (!properties.initialize()) return;
        Files.createDirectories(properties.databasePath().getParent());
        write(connection -> {
            String schema = """
                CREATE TABLE IF NOT EXISTS ticket_types(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE);
                CREATE TABLE IF NOT EXISTS tickets(
                  id INTEGER PRIMARY KEY, title TEXT NOT NULL,
                  type_id INTEGER NOT NULL REFERENCES ticket_types(id),
                  status TEXT NOT NULL CHECK(status IN ('aberto','em_andamento','concluido','cancelado')),
                  priority TEXT NOT NULL CHECK(priority IN ('baixa','media','alta','critica')),
                  technician TEXT, technical_notes TEXT, opened_at TEXT NOT NULL, closed_at TEXT,
                  usuario TEXT, CHECK(closed_at IS NULL OR closed_at >= opened_at));
                CREATE INDEX IF NOT EXISTS ix_tickets_opened ON tickets(opened_at);
                CREATE INDEX IF NOT EXISTS ix_tickets_closed ON tickets(closed_at);
                CREATE INDEX IF NOT EXISTS ix_tickets_status ON tickets(status);
                CREATE TABLE IF NOT EXISTS clientes(id TEXT PRIMARY KEY, nome TEXT NOT NULL, telefone TEXT NOT NULL, setor TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS horarios_atendimento(id TEXT PRIMARY KEY, setor TEXT NOT NULL,
                  dia_semana TEXT NOT NULL, horario_inicio TEXT NOT NULL, horario_fim TEXT NOT NULL,
                  CHECK(horario_inicio < horario_fim));
                """;
            try (var statement = connection.createStatement()) {
                for (String sql : schema.split(";")) if (!sql.isBlank()) statement.execute(sql);
                boolean hasUsuario = query(connection, "PRAGMA table_info(tickets)", List.of()).stream()
                        .anyMatch(row -> "usuario".equals(row.get("name")));
                if (!hasUsuario) statement.execute("ALTER TABLE tickets ADD COLUMN usuario TEXT");
            }
            String[] types = {"Hardware", "Software", "Rede", "Acesso/Senha", "Impressora", "E-mail", "Telefonia", "ERP"};
            // Mantém os IDs do banco existente e nunca substitui seus registros.
            if (((Number) query(connection, "SELECT COUNT(*) AS n FROM ticket_types", List.of()).getFirst().get("n")).intValue() == 0) {
                for (int i = 0; i < types.length; i++) {
                    try (var statement = prepare(connection, "INSERT INTO ticket_types(id,name) VALUES (?,?)", List.of(i + 1, types[i]))) { statement.executeUpdate(); }
                }
            }
            return null;
        });
    }
}
