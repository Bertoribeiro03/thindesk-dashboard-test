package com.pfc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import java.nio.file.Path;
import java.util.UUID;
import com.pfc.config.SecurityConfig;
import com.pfc.config.ThindeskProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class SecurityTest {
    static final Path DB=ApplicationTest.databasePath("security");
    static final String PASSWORD=UUID.randomUUID().toString();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("thindesk.db-path",DB::toString); registry.add("thindesk.initialize",()->"true"); registry.add("thindesk.seed",()->"false"); registry.add("thindesk.security-enabled",()->"true"); registry.add("thindesk.username",()->"operador"); registry.add("thindesk.password",()->PASSWORD); }
    @Autowired MockMvc mvc;
    @Test void credentialsAreRequiredWhenLoginIsEnabled() {
        var properties=new ThindeskProperties(DB.toString(),"local_sqlite",true,false,true,"","");
        assertThrows(IllegalStateException.class,()->new SecurityConfig().users(properties,new BCryptPasswordEncoder()));
    }
    @Test void authenticationAndLogout() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/api/v1/health")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("HTTP_ERROR"));
        mvc.perform(get("/login")).andExpect(status().isOk());
        var login=mvc.perform(post("/login").with(csrf()).param("username","operador").param("password",PASSWORD)).andExpect(redirectedUrl("/")).andReturn();
        var session=(MockHttpSession)login.getRequest().getSession(false);
        mvc.perform(get("/").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
    }
}
