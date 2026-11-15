package com.pfc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfc.data.Database;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import java.util.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationTest {
    static final Path DB = databasePath("api");
    static Path databasePath(String prefix) {
        try { Path directory=Path.of("target/test-data"); Files.createDirectories(directory); return Files.createTempDirectory(directory,prefix).resolve("tickets.db").toAbsolutePath(); }
        catch(Exception exception) { throw new IllegalStateException(exception); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("thindesk.db-path", DB::toString); registry.add("thindesk.initialize", () -> "true");
        registry.add("thindesk.seed", () -> "false"); registry.add("thindesk.security-enabled", () -> "false");
    }
    @Autowired MockMvc mvc;
    @Autowired Database database;
    @Autowired ObjectMapper mapper;

    @BeforeEach void fixture() {
        database.write(connection -> {
            try(var sql=connection.createStatement()) {
                sql.executeUpdate("DELETE FROM tickets"); sql.executeUpdate("DELETE FROM ticket_types"); sql.executeUpdate("DELETE FROM clientes"); sql.executeUpdate("DELETE FROM horarios_atendimento");
                sql.executeUpdate("INSERT INTO ticket_types VALUES (1,'Hardware'),(2,'Rede'),(3,'Software')");
                sql.executeUpdate("""
                    INSERT INTO tickets(id,title,type_id,status,priority,technician,technical_notes,opened_at,closed_at) VALUES
                    (1,'A',1,'concluido','alta','Ana','Troca','2026-01-05 08:00:00','2026-01-05 12:00:00'),
                    (2,'B',1,'concluido','media','Ana','Driver','2026-01-06 08:00:00','2026-01-07 08:00:00'),
                    (3,'C',2,'aberto','baixa',NULL,NULL,'2026-01-06 09:00:00',NULL),
                    (4,'D',2,'em_andamento','alta','Bruno','Analisando','2026-01-10 10:00:00',NULL),
                    (5,'E',1,'cancelado','baixa',NULL,NULL,'2026-02-01 10:00:00','2026-02-01 11:00:00')
                    """);
            } return null;
        });
    }
    JsonNode getJson(String url) throws Exception { return mapper.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }

    @Test void healthAndSummary() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.database").value("ok")).andExpect(jsonPath("$.data_source").value("local_sqlite"));
        var data=getJson("/api/v1/dashboard/summary").get("data");
        assertEquals(5,data.get("total").asInt()); assertEquals(1,data.get("abertos").asInt()); assertEquals(1,data.get("em_andamento").asInt());
        assertEquals(2,data.get("concluidos").asInt()); assertEquals(1,data.get("cancelados").asInt()); assertEquals(2,data.get("backlog").asInt());
        assertEquals(40.0,data.get("taxa_resolucao_pct").asDouble()); assertEquals(14.0,data.get("tempo_medio_resolucao_h").asDouble());
    }
    @Test void filtersAndEmpty() throws Exception {
        assertEquals(2,getJson("/api/v1/dashboard/summary?status=aberto,em_andamento").at("/data/total").asInt());
        assertEquals(2,getJson("/api/v1/dashboard/summary?type_id=1&start=2026-01-01&end=2026-01-31").at("/data/total").asInt());
        var empty=getJson("/api/v1/dashboard/summary?start=2020-01-01&end=2020-01-31"); assertEquals(0,empty.at("/data/total").asInt()); assertTrue(empty.at("/data/tempo_medio_resolucao_h").isNull());
        assertEquals(0,getJson("/api/v1/dashboard/summary?type_id=999999999999999999999").at("/data/total").asInt());
        var filters=getJson("/api/v1/dashboard/summary?status=em_andamento,aberto,aberto&type_id=2,1,2").get("filters");
        assertEquals("[\"aberto\",\"em_andamento\"]",filters.get("status").toString()); assertEquals("[1,2]",filters.get("type_id").toString());
    }
    @ParameterizedTest @ValueSource(strings={"2026-13-40","ontem","20260105","1700000000","2026-01-05T00:00:00","0000-01-01"})
    void malformedDates(String date) throws Exception { mvc.perform(get("/api/v1/dashboard/summary").param("start",date)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.error.details[0].param").value("start")); }
    @ParameterizedTest @ValueSource(strings={"abc","0","-1","1.5","1,,2","","1 OR 1=1"})
    void invalidTypes(String value) throws Exception { mvc.perform(get("/api/v1/dashboard/summary").param("type_id",value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_TYPE_ID")); }
    @Test void validationOrderAndPeriods() throws Exception {
        mvc.perform(get("/api/v1/dashboard/summary?start=2026-02-01&end=2026-01-01&status=x&type_id=abc")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_PERIOD"));
        mvc.perform(get("/api/v1/dashboard/summary?start=2020-01-01&end=2026-01-01&status=x&type_id=abc")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("PERIOD_TOO_LONG"));
        mvc.perform(get("/api/v1/dashboard/summary?status=xpto&type_id=abc")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_STATUS")).andExpect(jsonPath("$.error.details.invalid[0]").value("xpto")).andExpect(jsonPath("$.error.details.allowed",hasSize(4)));
        getJson("/api/v1/dashboard/summary?start=2024-01-01&end=2026-01-01");
    }
    @Test void seriesDayMonthWeek() throws Exception {
        assertEquals(mapper.readTree(""" 
          [{"period":"2026-01-05","abertos":1,"atendidos":1},{"period":"2026-01-06","abertos":2,"atendidos":0},{"period":"2026-01-07","abertos":0,"atendidos":1}]
          """),getJson("/api/v1/dashboard/timeseries?start=2026-01-05&end=2026-01-07").get("data"));
        assertEquals(mapper.readTree("""
          [{"period":"2026-01","abertos":4,"atendidos":2},{"period":"2026-02","abertos":1,"atendidos":0}]
          """),getJson("/api/v1/dashboard/timeseries?granularity=month").get("data"));
        assertEquals(mapper.readTree("""
          [{"period":"2025-W52","abertos":0,"atendidos":0},{"period":"2026-W00","abertos":0,"atendidos":0},{"period":"2026-W01","abertos":3,"atendidos":2}]
          """),getJson("/api/v1/dashboard/timeseries?granularity=week&start=2025-12-29&end=2026-01-07").get("data"));
        mvc.perform(get("/api/v1/dashboard/timeseries?granularity=year")).andExpect(status().isBadRequest());
    }
    @Test void closingDateAndInclusiveEnd() throws Exception {
        database.write(connection -> { try(var sql=connection.createStatement()) {sql.executeUpdate("INSERT INTO tickets(id,title,type_id,status,priority,opened_at,closed_at) VALUES (6,'F',1,'concluido','media','2025-12-30 08:00:00','2026-01-06 23:59:59')");} return null; });
        var series=getJson("/api/v1/dashboard/timeseries?start=2026-01-06&end=2026-01-06").at("/data/0"); assertEquals(2,series.get("abertos").asInt()); assertEquals(1,series.get("atendidos").asInt());
        assertEquals(2,getJson("/api/v1/dashboard/summary?start=2026-01-06&end=2026-01-06").at("/data/total").asInt());
    }
    @Test void distributionRankingPaginationAndMetadata() throws Exception {
        var rows=getJson("/api/v1/dashboard/status-distribution").get("data"); int sum=0; double pct=0; for(var row:rows) {sum+=row.get("count").asInt(); pct+=row.get("pct").asDouble();} assertEquals(5,sum); assertEquals(100,pct);
        mvc.perform(get("/api/v1/dashboard/top-types")).andExpect(jsonPath("$.data[0].type").value("Hardware")).andExpect(jsonPath("$.data[0].count").value(3)).andExpect(jsonPath("$.data[1].type").value("Rede"));
        mvc.perform(get("/api/v1/tickets?page_size=2")).andExpect(jsonPath("$.items",hasSize(2))).andExpect(jsonPath("$.pages").value(3)).andExpect(jsonPath("$.items[0].id").value(5));
        mvc.perform(get("/api/v1/tickets?page=99999999999999999&page_size=100")).andExpect(jsonPath("$.items",hasSize(0)));
        mvc.perform(get("/api/v1/meta/filters")).andExpect(jsonPath("$.data.types[0].name").value("Hardware")).andExpect(jsonPath("$.data.date_range.min").value("2026-01-05"));
    }
    @ParameterizedTest @ValueSource(strings={"/api/v1/tickets?page=0","/api/v1/tickets?page_size=0","/api/v1/tickets?page_size=101","/api/v1/tickets?page=abc","/api/v1/dashboard/top-types?limit=0","/api/v1/dashboard/top-types?limit=51"})
    void invalidPagination(String url) throws Exception { mvc.perform(get(url)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR")); }
    @ParameterizedTest @ValueSource(strings={"4","9223372036854775807","999999999999999999999"})
    void pageBeyondTotalIsEmpty(String page) throws Exception { mvc.perform(get("/api/v1/tickets").param("page",page).param("page_size","2")).andExpect(status().isOk()).andExpect(jsonPath("$.items",hasSize(0))).andExpect(jsonPath("$.total").value(5)); }
    @ParameterizedTest @ValueSource(strings={"/","/dashboard","/chamados","/chamados/novo","/chamados/atualizar/1","/clientes","/clientes/novo","/ajustes-horarios","/tecnicos","/tipo-chamado","/docs","/login"})
    void templatesRender(String url) throws Exception { mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html")).andExpect(content().string(not(containsString("th:field=")))).andExpect(content().string(not(containsString("th:replace=")))); }
    @ParameterizedTest @ValueSource(strings={"/css/style.css","/css/dashboard.css","/js/script.js","/js/dashboard.js","/js/api-docs.js","/images/logo.png"})
    void resourcesExist(String url) throws Exception { mvc.perform(get(url)).andExpect(status().isOk()); }
    @Test void notFoundMethodsAndOpenApi() throws Exception {
        mvc.perform(get("/api/v1/nao-existe")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get("/nao-existe")).andExpect(status().isNotFound()).andExpect(content().string(containsString("Não foi possível abrir esta página")));
        mvc.perform(get("/clientes/deletar/1")).andExpect(status().isMethodNotAllowed());
        var spec=getJson("/openapi.json"); assertTrue(spec.at("/paths/~1api~1v1~1dashboard~1summary/get/responses/400").isObject()); assertTrue(spec.at("/paths/~1api~1v1~1dashboard~1summary/get/responses/422").isMissingNode());
        assertEquals("1.1.0",spec.at("/info/version").asText());
    }
    @Test void ticketCrudAndRedirects() throws Exception {
        String body=""" 
          {"descricao":"Rede indisponível","tipo":"Rede","status":"ABERTO","prioridade":"alta","usuario":"Solicitante","consideracoesTecnicas":"Analisar cabo"}
          """;
        var created=mvc.perform(post("/api/chamados").with(csrf()).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn();
        String id=mapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/chamados/atualizar/"+id)).andExpect(status().isOk()).andExpect(model().attribute("chamado", hasProperty("descricao",is("Rede indisponível"))));
        mvc.perform(post("/chamados/atualizar/"+id).with(csrf()).param("descricao","Rede resolvida").param("tipo","Rede").param("status","concluido").param("prioridade","alta").param("consideracoesTecnicas","Cabo reparado")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/chamados"));
        var saved=getJson("/api/chamados/"+id); assertEquals("concluido",saved.get("status").asText()); assertFalse(saved.get("encerramento").isNull()); assertEquals("Cabo reparado",saved.get("consideracoesTecnicas").asText());
        mvc.perform(post("/chamados/cancelar/"+id).with(csrf())).andExpect(redirectedUrl("/chamados"));
    }
    @Test void clientAndScheduleForms() throws Exception {
        mvc.perform(post("/clientes/novo").with(csrf()).param("nome","Cliente de teste").param("telefone","11999999999").param("setor","Financeiro")).andExpect(redirectedUrl("/clientes"));
        String id=getJson("/api/clientes").get(0).get("id").asText();
        mvc.perform(get("/clientes/editar/"+id)).andExpect(status().isOk());
        mvc.perform(post("/clientes/editar/"+id).with(csrf()).param("nome","Cliente atualizado").param("telefone","11888888888").param("setor","TI")).andExpect(redirectedUrl("/clientes"));
        mvc.perform(get("/clientes")).andExpect(content().string(containsString("Cliente atualizado")));
        mvc.perform(post("/clientes/deletar/"+id).with(csrf())).andExpect(redirectedUrl("/clientes"));
        mvc.perform(post("/ajustes-horarios/salvar").with(csrf()).param("setor","TI").param("diaSemana","Segunda-feira").param("horarioInicio","18:00").param("horarioFim","08:00")).andExpect(status().isOk()).andExpect(content().string(containsString("O horário final deve ser posterior")));
        mvc.perform(post("/ajustes-horarios/salvar").with(csrf()).param("setor","TI").param("diaSemana","Segunda-feira").param("horarioInicio","08:00").param("horarioFim","18:00")).andExpect(redirectedUrl("/ajustes-horarios"));
        String schedule=getJson("/api/ajustes-horarios").get(0).get("id").asText();
        mvc.perform(get("/ajustes-horarios/editar/"+schedule)).andExpect(status().isOk());
        mvc.perform(post("/ajustes-horarios/deletar/"+schedule).with(csrf())).andExpect(redirectedUrl("/ajustes-horarios"));
    }
    @Test void csrfValidationAndHtmlEscape() throws Exception {
        mvc.perform(post("/api/clientes").contentType("application/json").content("{}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("HTTP_ERROR"));
        mvc.perform(post("/api/clientes").with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/clientes/novo").with(csrf()).param("nome","<script>alert(1)</script>").param("telefone","123").param("setor","TI")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/clientes")).andExpect(content().string(containsString("&lt;script&gt;"))).andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
        mvc.perform(get("/chamados/atualizar/inexistente")).andExpect(status().isNotFound());
    }
    @Test void nullPriorityIsValidationError() throws Exception {
        mvc.perform(post("/api/chamados").with(csrf()).contentType("application/json").content("{\"descricao\":\"Teste\",\"tipo\":\"Rede\",\"status\":\"aberto\",\"prioridade\":null}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    @Test void unsupportedContentTypeKeepsHttpStatus() throws Exception {
        mvc.perform(post("/api/chamados").with(csrf()).contentType("text/plain").content("teste"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.error.code").value("HTTP_ERROR"));
    }
    @Test void readOnlyAndConsistency() throws Exception {
        assertThrows(java.sql.SQLException.class,()-> { try(var connection=database.open(true);var sql=connection.createStatement()) {sql.executeUpdate("DELETE FROM tickets");} });
        var summary=getJson("/api/v1/dashboard/summary").get("data"); int total=summary.get("total").asInt();
        assertEquals(total,summary.get("abertos").asInt()+summary.get("em_andamento").asInt()+summary.get("concluidos").asInt()+summary.get("cancelados").asInt());
        assertEquals(total,getJson("/api/v1/tickets").get("total").asInt());
    }
}
