package com.pfc.service;

import com.pfc.api.ApiError;
import com.pfc.api.Filters;
import com.pfc.data.Database;
import com.pfc.model.Forms.*;
import org.springframework.stereotype.Service;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ManagementService {
    public static final List<String> DAYS = List.of("Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado", "Domingo");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String TICKET_SELECT = "SELECT t.*,tt.name AS tipo FROM tickets t JOIN ticket_types tt ON tt.id=t.type_id";
    private final Database database;
    public ManagementService(Database database) { this.database = database; }
    public List<Chamado> chamados() { return database.query(TICKET_SELECT + " ORDER BY t.opened_at DESC,t.id DESC", List.of()).stream().map(this::ticket).toList(); }
    public Chamado chamado(String id) { return ticket(one(TICKET_SELECT + " WHERE t.id=?", id)); }
    private Chamado ticket(Map<String, Object> row) {
        var item = new Chamado(); item.setId(row.get("id").toString()); item.setDescricao((String) row.get("title"));
        item.setTipo((String) row.get("tipo")); item.setStatus((String) row.get("status")); item.setPrioridade((String) row.get("priority"));
        item.setTecnico((String) row.get("technician")); item.setUsuario((String) row.get("usuario")); item.setConsideracoesTecnicas((String) row.get("technical_notes"));
        item.setAbertura((String) row.get("opened_at")); item.setEncerramento((String) row.get("closed_at")); return item;
    }
    public Chamado saveChamado(String id, Chamado item) {
        String status = normalize(item.getStatus());
        if (!Filters.STATUSES.containsKey(status)) throw ApiError.validation("status", "Status inválido.");
        if (!List.of("baixa", "media", "alta", "critica").contains(item.getPrioridade())) throw ApiError.validation("prioridade", "Prioridade inválida.");
        return database.write(connection -> {
            String opened = LocalDateTime.now().format(TIMESTAMP), closed = null;
            if (id != null) {
                var existing = Database.query(connection, TICKET_SELECT + " WHERE t.id=?", List.of(id));
                if (existing.isEmpty()) throw notFound();
                opened = (String) existing.getFirst().get("opened_at");
                closed = (String) existing.getFirst().get("closed_at");
            }
            if (status.equals("concluido") || status.equals("cancelado")) {
                if (closed == null) closed = LocalDateTime.now().format(TIMESTAMP);
            } else closed = null;
            var types = Database.query(connection, "SELECT id FROM ticket_types WHERE name=?", List.of(item.getTipo().strip()));
            if (types.isEmpty()) throw ApiError.validation("tipo", "Selecione um tipo cadastrado.");
            var values = new ArrayList<Object>(Arrays.asList(item.getDescricao().strip(), types.getFirst().get("id"), status, item.getPrioridade(),
                    blankToNull(item.getTecnico()), blankToNull(item.getConsideracoesTecnicas()), opened, closed, blankToNull(item.getUsuario())));
            String sql;
            if (id == null) sql = "INSERT INTO tickets(title,type_id,status,priority,technician,technical_notes,opened_at,closed_at,usuario) VALUES (?,?,?,?,?,?,?,?,?)";
            else { sql = "UPDATE tickets SET title=?,type_id=?,status=?,priority=?,technician=?,technical_notes=?,opened_at=?,closed_at=?,usuario=? WHERE id=?"; values.add(id); }
            execute(connection, sql, values);
            String savedId = id == null ? Database.query(connection, "SELECT last_insert_rowid() AS id", List.of()).getFirst().get("id").toString() : id;
            return ticket(Database.query(connection, TICKET_SELECT + " WHERE t.id=?", List.of(savedId)).getFirst());
        });
    }
    public void cancelChamado(String id) { var item = chamado(id); item.setStatus("cancelado"); saveChamado(id, item); }
    public List<Cliente> clientes() { return database.query("SELECT * FROM clientes ORDER BY nome,id", List.of()).stream().map(this::client).toList(); }
    public Cliente cliente(String id) { return client(one("SELECT * FROM clientes WHERE id=?", id)); }
    private Cliente client(Map<String, Object> row) {
        var item = new Cliente(); item.setId((String) row.get("id")); item.setNome((String) row.get("nome"));
        item.setTelefone((String) row.get("telefone")); item.setSetor((String) row.get("setor")); return item;
    }
    public Cliente saveCliente(String id, Cliente item) {
        if (id != null) cliente(id);
        String key = id == null ? UUID.randomUUID().toString() : id;
        database.write(connection -> {
            execute(connection, "INSERT INTO clientes(id,nome,telefone,setor) VALUES (?,?,?,?) ON CONFLICT(id) DO UPDATE SET nome=excluded.nome,telefone=excluded.telefone,setor=excluded.setor",
                    List.of(key, item.getNome().strip(), item.getTelefone().strip(), item.getSetor().strip())); return null;
        });
        return cliente(key);
    }
    public void deleteCliente(String id) { cliente(id); delete("DELETE FROM clientes WHERE id=?", id); }
    public List<Horario> horarios() { return database.query("SELECT * FROM horarios_atendimento ORDER BY setor,dia_semana,horario_inicio", List.of()).stream().map(this::schedule).toList(); }
    public Horario horario(String id) { return schedule(one("SELECT * FROM horarios_atendimento WHERE id=?", id)); }
    private Horario schedule(Map<String, Object> row) {
        var item = new Horario(); item.setId((String) row.get("id")); item.setSetor((String) row.get("setor"));
        item.setDiaSemana((String) row.get("dia_semana")); item.setHorarioInicio((String) row.get("horario_inicio")); item.setHorarioFim((String) row.get("horario_fim")); return item;
    }
    public Horario saveHorario(String id, Horario item) {
        if (!DAYS.contains(item.getDiaSemana())) throw ApiError.validation("diaSemana", "Selecione um dia da semana válido.");
        try {
            if (!item.getHorarioInicio().matches("\\d{2}:\\d{2}") || !item.getHorarioFim().matches("\\d{2}:\\d{2}")
                    || !LocalTime.parse(item.getHorarioInicio()).isBefore(LocalTime.parse(item.getHorarioFim()))) throw new IllegalArgumentException();
        } catch (RuntimeException exception) { throw ApiError.validation("horarioFim", "O horário final deve ser posterior ao inicial; use HH:mm."); }
        if (id != null) horario(id);
        String key = id == null ? UUID.randomUUID().toString() : id;
        database.write(connection -> {
            execute(connection, "INSERT INTO horarios_atendimento(id,setor,dia_semana,horario_inicio,horario_fim) VALUES (?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET setor=excluded.setor,dia_semana=excluded.dia_semana,horario_inicio=excluded.horario_inicio,horario_fim=excluded.horario_fim",
                    List.of(key, item.getSetor().strip(), item.getDiaSemana(), item.getHorarioInicio(), item.getHorarioFim())); return null;
        });
        return horario(key);
    }
    public void deleteHorario(String id) { horario(id); delete("DELETE FROM horarios_atendimento WHERE id=?", id); }
    private Map<String, Object> one(String sql, String id) { var rows = database.query(sql, List.of(id)); if (rows.isEmpty()) throw notFound(); return rows.getFirst(); }
    private void delete(String sql, String id) { database.write(connection -> { execute(connection, sql, List.of(id)); return null; }); }
    private static void execute(Connection connection, String sql, List<?> values) throws SQLException { try (var statement = Database.prepare(connection, sql, values)) { statement.executeUpdate(); } }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static String normalize(String value) { return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").strip().toLowerCase(Locale.ROOT).replace(' ', '_'); }
    private static ApiError notFound() { return new ApiError(404, "NOT_FOUND", "Registro não encontrado."); }
}
