package ru.codex.codextest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class RelatedTaskUrlMigrationTests {
    @TempDir Path temporaryDirectory;

    @Test
    void upgradesExistingBugWithoutChangingInitialChangeset() throws Exception {
        String master = new ClassPathResource("db/changelog/db.changelog-master.xml")
                .getContentAsString(StandardCharsets.UTF_8);
        // Apply the actual initial changeSet under its original logical path, without the new include.
        String initial = master
                .replace("<databaseChangeLog ", "<databaseChangeLog logicalFilePath=\"db/changelog/db.changelog-master.xml\" ")
                .replaceAll("(?m)^\\s*<include[^>]+/>\\s*$", "");
        Path oldChangelog = temporaryDirectory.resolve("initial.xml");
        Files.writeString(oldChangelog, initial);
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:url-upgrade-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            migrate(dataSource, oldChangelog.toUri().toString());
            jdbc.update("""
                    insert into bug (header, status, priority, created_at, updated_at)
                    values ('Existing bug', 'FIXED', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            String checksum = jdbc.queryForObject(
                    "select md5sum from databasechangelog where id = '1-create-bug'", String.class);

            migrate(dataSource, "classpath:db/changelog/db.changelog-master.xml");

            assertThat(jdbc.queryForObject("select header from bug", String.class)).isEqualTo("Existing bug");
            assertThat(jdbc.queryForObject("select status from bug", String.class)).isEqualTo("FIXED");
            assertThat(jdbc.queryForObject("select related_task_url from bug", String.class)).isNull();
            assertThat(jdbc.queryForObject(
                    "select md5sum from databasechangelog where id = '1-create-bug'", String.class))
                    .isEqualTo(checksum);
            assertThat(jdbc.queryForObject("select count(*) from databasechangelog", Integer.class)).isEqualTo(3);
            String longUrl = "https://tracker.invalid/?q=" + "x".repeat(3000);
            jdbc.update("update bug set related_task_url = ?", longUrl);
            assertThat(jdbc.queryForObject("select related_task_url from bug", String.class)).isEqualTo(longUrl);
        } finally {
            jdbc.execute("shutdown");
        }
    }

    private void migrate(DriverManagerDataSource dataSource, String changelog) throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.setChangeLog(changelog);
        liquibase.afterPropertiesSet();
    }
}
