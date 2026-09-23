package ru.codex.codextest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import ru.codex.codextest.model.Bug;
import ru.codex.codextest.repository.BugRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RelatedTaskUrlApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired BugRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    private Map<String, Object> oldBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("header", "Bug");
        body.put("priority", "HIGH");
        return body;
    }

    private Map<String, Object> body(String url) {
        Map<String, Object> body = oldBody();
        body.put("relatedTaskUrl", url);
        return body;
    }

    private Bug create(Map<String, Object> body) throws Exception {
        mvc.perform(post("/api/bugs").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"));
        return repository.findAll().get(0);
    }

    @Test
    void oldJsonCreatesBugWithoutUrl() throws Exception {
        Bug bug = create(oldBody());
        assertThat(bug.getRelatedTaskUrl()).isNull();
        mvc.perform(get("/api/bugs/" + bug.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedTaskUrl").value(nullValue()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "https://tracker.company.local/TASK-123 | https://tracker.company.local/TASK-123",
            "http://tracker.company.local/TASK-123 | http://tracker.company.local/TASK-123",
            "tracker.company.local/TASK-123 | https://tracker.company.local/TASK-123",
            "HTTPS://example.invalid/task?q=1#note | HTTPS://example.invalid/task?q=1#note",
            "http://localhost:8080/task | http://localhost:8080/task",
            "https://пример.рф/задача | https://пример.рф/задача",
            "https://[::1]:8080/task | https://[::1]:8080/task"
    })
    void createsAndReturnsNormalizedUrl(String input, String expected) throws Exception {
        Bug bug = create(body(input));
        assertThat(bug.getRelatedTaskUrl()).isEqualTo(expected);
        mvc.perform(get("/api/bugs/" + bug.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.relatedTaskUrl").value(expected));
        mvc.perform(get("/api/bugs"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].relatedTaskUrl").value(expected));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " \t\n "})
    void nullAndBlankAreOptionalAtCreation(String input) throws Exception {
        assertThat(create(body(input)).getRelatedTaskUrl()).isNull();
    }

    @Test
    void putSetsAndReplacesUrlWithoutChangingStatusAndUsesExistingTimestamps() throws Exception {
        Bug bug = create(oldBody());
        long id = bug.getId();
        mvc.perform(patch("/api/bugs/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FIXED\"}"))
                .andExpect(status().isOk());
        // A deterministic old value proves that editing only the URL triggers @PreUpdate.
        Instant previous = Instant.parse("2000-01-01T00:00:00Z");
        jdbc.update("update bug set updated_at = ? where id = ?", java.sql.Timestamp.from(previous), id);

        mvc.perform(put("/api/bugs/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body("tracker.company.local/ONE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedTaskUrl").value("https://tracker.company.local/ONE"))
                .andExpect(jsonPath("$.status").value("FIXED"));
        Bug afterSet = repository.findById(id).orElseThrow();
        assertThat(afterSet.getUpdatedAt()).isAfter(previous);
        assertThat(afterSet.getCreatedAt()).isEqualTo(bug.getCreatedAt());

        mvc.perform(put("/api/bugs/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body("http://other.invalid/TWO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedTaskUrl").value("http://other.invalid/TWO"))
                .andExpect(jsonPath("$.status").value("FIXED"));
        assertThat(repository.findById(id).orElseThrow().getRelatedTaskUrl())
                .isEqualTo("http://other.invalid/TWO");
        mvc.perform(patch("/api/bugs/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedTaskUrl").value("http://other.invalid/TWO"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " \t\n "})
    void putClearsUrlWithNullOrBlank(String input) throws Exception {
        Bug bug = create(body("https://tracker.invalid/ONE"));
        mvc.perform(put("/api/bugs/" + bug.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body(input))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedTaskUrl").value(nullValue()));
        assertThat(repository.findById(bug.getId()).orElseThrow().getRelatedTaskUrl()).isNull();
    }

    @Test
    void oldPutWithoutNewFieldClearsUrlAndRetainsStatus() throws Exception {
        Bug bug = create(body("https://tracker.invalid/ONE"));
        mvc.perform(patch("/api/bugs/" + bug.getId() + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FIXED\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/bugs/" + bug.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(oldBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedTaskUrl").value(nullValue()))
                .andExpect(jsonPath("$.status").value("FIXED"));
        assertThat(repository.findById(bug.getId()).orElseThrow().getRelatedTaskUrl()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a url", "https://", "http:///task", "ftp://example.invalid/task",
            "javascript:alert(1)", "https://host.invalid/%zz", "https://host.invalid:wrong/task",
            "https://host.invalid:65536/task", "https://host.invalid/line\nbreak"})
    void invalidCreateReturnsFieldErrorAndDoesNotPersist(String input) throws Exception {
        mvc.perform(post("/api/bugs").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body(input))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.relatedTaskUrl").isString());
        assertThat(repository.count()).isZero();
    }

    @Test
    void invalidPutDoesNotChangeExistingBug() throws Exception {
        Bug before = create(body("https://tracker.invalid/ONE"));
        Map<String, Object> invalid = body("not a url");
        invalid.put("header", "Should not be saved");
        mvc.perform(put("/api/bugs/" + before.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.relatedTaskUrl").isString());
        Bug after = repository.findById(before.getId()).orElseThrow();
        assertThat(after.getHeader()).isEqualTo(before.getHeader());
        assertThat(after.getRelatedTaskUrl()).isEqualTo(before.getRelatedTaskUrl());
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
    }

    @Test
    void urlLongerThanDefaultVarcharIsNotTruncatedOrRejected() throws Exception {
        String url = "https://tracker.invalid/task?q=" + "x".repeat(3000);
        Bug bug = create(body(url));
        assertThat(bug.getRelatedTaskUrl()).isEqualTo(url);
        mvc.perform(get("/api/bugs/" + bug.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedTaskUrl").value(url));
    }
}
