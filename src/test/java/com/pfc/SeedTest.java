package com.pfc;

import com.pfc.data.Database;
import com.pfc.service.ExampleSeed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SeedTest {
    static final Path DB=ApplicationTest.databasePath("seed");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("thindesk.db-path",DB::toString); registry.add("thindesk.initialize",()->"true"); registry.add("thindesk.seed",()->"true"); registry.add("thindesk.security-enabled",()->"false"); }
    @Autowired Database database;
    @Autowired ExampleSeed seed;
    @Test void generatesValidExamplesWithoutReplacingExistingData() {
        var before=database.query("SELECT * FROM tickets ORDER BY id", List.of()); assertEquals(500,before.size());
        for(var row:before) {
            String status=(String)row.get("status");
            if(status.equals("aberto")) { assertNull(row.get("technician")); assertNull(row.get("technical_notes")); }
            assertEquals(status.equals("concluido") || status.equals("cancelado"),row.get("closed_at")!=null);
            if(row.get("closed_at")!=null) assertTrue(row.get("closed_at").toString().compareTo(row.get("opened_at").toString())>=0);
        }
        seed.run(new DefaultApplicationArguments());
        assertEquals(before,database.query("SELECT * FROM tickets ORDER BY id",List.of()));
    }
}
