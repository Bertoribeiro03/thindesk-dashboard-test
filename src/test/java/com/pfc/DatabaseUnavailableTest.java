package com.pfc;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Path;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class DatabaseUnavailableTest {
    static final Path MISSING=ApplicationTest.databasePath("missing").resolveSibling("interno-inexistente.db");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("thindesk.db-path",MISSING::toString); registry.add("thindesk.initialize",()->"false"); registry.add("thindesk.security-enabled",()->"false"); }
    @Autowired MockMvc mvc;
    @ParameterizedTest @ValueSource(strings={"/health","/meta/filters","/dashboard/summary","/dashboard/timeseries","/dashboard/status-distribution","/dashboard/top-types","/tickets"})
    void missingDatabaseReturns503(String route) throws Exception { mvc.perform(get("/api/v1"+route)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("DATABASE_UNAVAILABLE")).andExpect(jsonPath("$.error.details").value(nullValue())).andExpect(content().string(not(containsString("interno-inexistente")))); }
}
