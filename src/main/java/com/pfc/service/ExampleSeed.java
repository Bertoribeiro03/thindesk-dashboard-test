package com.pfc.service;

import com.pfc.config.ThindeskProperties;
import com.pfc.data.Database;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class ExampleSeed implements ApplicationRunner {
    private final Database database;
    private final ThindeskProperties properties;
    public ExampleSeed(Database database, ThindeskProperties properties) { this.database=database; this.properties=properties; }
    @Override public void run(ApplicationArguments args) {
        if (!properties.seed()) return;
        database.write(connection -> {
            int count = ((Number) Database.query(connection, "SELECT COUNT(*) AS n FROM tickets", List.of()).getFirst().get("n")).intValue();
            if (count > 0) return null;
            var types = Database.query(connection, "SELECT id,name FROM ticket_types ORDER BY id", List.of());
            var random = new Random(42);
            var now = LocalDateTime.now().withNano(0);
            var format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String[] technicians = {"Ana Souza", "Bruno Lima", "Carla Dias", "Diego Rocha"};
            String[] notes = {"Equipamento substituído.", "Reinstalação do driver.", "Permissão ajustada no AD.", "Aguardando peça do fornecedor.", "Cabo de rede reparado.", "Atualização aplicada."};
            int[] weights = {18,22,14,20,8,9,4,5};
            for (int i=1; i<=500; i++) {
                int choice=random.nextInt(100), type=0;
                while (type<weights.length-1 && choice>=weights[type]) { choice-=weights[type]; type++; }
                var selected=types.get(type%types.size());
                long ageSeconds=random.nextLong(180L*24*3600);
                var opened=now.minusSeconds(ageSeconds);
                int pick=random.nextInt(100);
                String status=ageSeconds<7200 ? "aberto" : pick<70 ? "concluido" : pick<82 ? "em_andamento" : pick<94 ? "aberto" : "cancelado";
                String closed=(status.equals("concluido") || status.equals("cancelado")) ? opened.plusSeconds(1800+random.nextLong(Math.min(ageSeconds,120*3600)-1799)).format(format) : null;
                var values=Arrays.asList("Chamado "+i+" - "+selected.get("name"), selected.get("id"), status, List.of("baixa","media","alta","critica").get(random.nextInt(4)),
                        status.equals("aberto") ? null : technicians[random.nextInt(4)], status.equals("aberto") ? null : notes[random.nextInt(notes.length)], opened.format(format), closed);
                try (var statement=Database.prepare(connection, "INSERT INTO tickets(title,type_id,status,priority,technician,technical_notes,opened_at,closed_at) VALUES (?,?,?,?,?,?,?,?)", values)) { statement.executeUpdate(); }
            }
            return null;
        });
    }
}
