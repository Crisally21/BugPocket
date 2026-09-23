package ru.codex.codextest.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.codex.codextest.dto.*;
import ru.codex.codextest.exception.BugNotFoundException;
import ru.codex.codextest.model.*;
import ru.codex.codextest.repository.BugRepository;

@Service
@Transactional(readOnly = true)
public class BugService {
    private final BugRepository repository;

    public BugService(BugRepository repository) {
        this.repository = repository;
    }

    public List<BugResponse> findAll(BugStatus status, BugPriority priority) {
        return repository.findFiltered(status, priority).stream().map(BugResponse::from).toList();
    }

    public BugResponse findById(long id) {
        return BugResponse.from(requireBug(id));
    }

    @Transactional
    public BugResponse create(BugRequest request) {
        Bug bug = new Bug();
        apply(bug, request);
        return BugResponse.from(repository.saveAndFlush(bug));
    }

    @Transactional
    public BugResponse update(long id, BugRequest request) {
        Bug bug = requireBug(id);
        apply(bug, request);
        repository.flush();
        return BugResponse.from(bug);
    }

    @Transactional
    public BugResponse changeStatus(long id, BugStatus status) {
        Bug bug = requireBug(id);
        bug.setStatus(status);
        repository.flush();
        return BugResponse.from(bug);
    }

    private Bug requireBug(long id) {
        return repository.findById(id).orElseThrow(() -> new BugNotFoundException(id));
    }

    private void apply(Bug bug, BugRequest request) {
        bug.setHeader(request.header().strip());
        bug.setSteps(request.steps());
        bug.setActualResult(request.actualResult());
        bug.setExpectedResult(request.expectedResult());
        bug.setEnvironment(request.environment());
        bug.setPriority(request.priority());
    }
}
