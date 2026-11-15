package com.pfc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.Path;

@Component
public record ThindeskProperties(
        @Value("${thindesk.db-path}") String dbPath,
        @Value("${thindesk.data-source}") String dataSource,
        @Value("${thindesk.initialize}") boolean initialize,
        @Value("${thindesk.seed}") boolean seed,
        @Value("${thindesk.security-enabled}") boolean securityEnabled,
        @Value("${thindesk.username}") String username,
        @Value("${thindesk.password}") String password) {
    public Path databasePath() { return Path.of(dbPath).toAbsolutePath().normalize(); }
    @Override public String toString() { return "ThindeskProperties[dataSource=" + dataSource + "]"; }
}
