package ru.codex.codextest.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.codex.codextest.dto.*;
import ru.codex.codextest.model.*;
import ru.codex.codextest.service.BugService;

@RestController
@RequestMapping("/api/bugs")
public class BugController {
    private final BugService service;

    public BugController(BugService service) {
        this.service = service;
    }

    @GetMapping
    public List<BugResponse> findAll(@RequestParam(required = false) BugStatus status,
                                    @RequestParam(required = false) BugPriority priority) {
        return service.findAll(status, priority);
    }

    @GetMapping("/{id}")
    public BugResponse findById(@PathVariable long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<BugResponse> create(@Valid @RequestBody BugRequest request) {
        BugResponse bug = service.create(request);
        return ResponseEntity.created(URI.create("/api/bugs/" + bug.id())).body(bug);
    }

    @PutMapping("/{id}")
    public BugResponse update(@PathVariable long id, @Valid @RequestBody BugRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public BugResponse changeStatus(@PathVariable long id, @Valid @RequestBody BugStatusRequest request) {
        return service.changeStatus(id, request.status());
    }
}
