package ru.codex.codextest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.codex.codextest.repository.AttachmentRepository;
import ru.codex.codextest.repository.BugRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BugApiTests {
    @Autowired MockMvc mvc;
    @Autowired BugRepository repository;
    @Autowired
    AttachmentRepository attachmentRepository;

    @BeforeEach
    void clearDatabase() {
        attachmentRepository.deleteAll();
        repository.deleteAll();
    }

    private String body(String header) {
        return """
                {"header":"%s","steps":"Open page","actualResult":"Error",
                 "expectedResult":"Page opens","environment":"Firefox","priority":"HIGH"}
                """.formatted(header);
    }

    @Test
    void createReadUpdateAndFilter() throws Exception {
        mvc.perform(post("/api/bugs").contentType(MediaType.APPLICATION_JSON).content(body("  Login error  ")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.header").value("Login error"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.createdAt").exists());
        var saved = repository.findAll().get(0);
        long id = saved.getId();
        mvc.perform(get("/api/bugs/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.steps").value("Open page"));
        mvc.perform(patch("/api/bugs/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FIXED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FIXED"));
        mvc.perform(put("/api/bugs/" + id).contentType(MediaType.APPLICATION_JSON).content(body("Updated")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.header").value("Updated"))
                .andExpect(jsonPath("$.status").value("FIXED"));
        mvc.perform(get("/api/bugs?status=FIXED&priority=HIGH"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/bugs?status=NEW")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/bugs?priority=LOW")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/bugs")).andExpect(jsonPath("$.length()").value(1));
        var updated = repository.findById(id).orElseThrow();
        assertThat(updated.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(saved.getUpdatedAt());
    }

    @Test
    void rejectsInvalidFieldsAndJson() throws Exception {
        mvc.perform(post("/api/bugs").contentType(MediaType.APPLICATION_JSON).content(body(" ")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.header").exists());
        mvc.perform(post("/api/bugs").contentType(MediaType.APPLICATION_JSON).content(body("x".repeat(201))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/bugs").contentType(MediaType.APPLICATION_JSON).content("{\"header\":\"Bug\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.priority").exists());
        mvc.perform(post("/api/bugs").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/bugs?status=UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/bugs/abc")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/bugs/1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    void missingBugReturns404ForReadAndUpdates() throws Exception {
        mvc.perform(get("/api/bugs/999")).andExpect(status().isNotFound());
        mvc.perform(put("/api/bugs/999").contentType(MediaType.APPLICATION_JSON).content(body("Bug")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/bugs/999/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FIXED\"}")).andExpect(status().isNotFound());
    }
}
