package com.qaas.bug;

import com.qaas.common.PagedResponse;
import com.qaas.execution.ExecutionStatus;
import com.qaas.execution.TestExecution;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BugDetectionService {
    private final BugRepository bugs;

    public BugDetectionService(BugRepository bugs) {
        this.bugs = bugs;
    }

    @Transactional
    public Bug detectFromExecution(TestExecution execution, UUID analysisId) {
        if (execution.getStatus() != ExecutionStatus.FAILED && execution.getStatus() != ExecutionStatus.ERROR) {
            return null;
        }
        String testName = execution.getTest().getName();
        String error = execution.getErrorMessage() != null ? execution.getErrorMessage() : "Unexpected failure";
        Severity severity = execution.getStatus() == ExecutionStatus.ERROR ? Severity.CRITICAL : Severity.HIGH;
        Bug bug = new Bug(
                execution.getId(),
                analysisId,
                "Test failure: " + testName,
                error,
                severity
        );
        return bugs.save(bug);
    }

    @Transactional(readOnly = true)
    public PagedResponse<BugDtos.BugResponse> getByAnalysis(UUID analysisId, Pageable pageable) {
        var paged = bugs.findByAnalysisId(analysisId, pageable);
        return PagedResponse.fromMapped(paged, paged.getContent().stream()
                .map(BugDtos.BugResponse::from).toList());
    }

    @Transactional
    public BugDtos.BugResponse updateStatus(UUID bugId, BugStatus newStatus) {
        Bug bug = bugs.findById(bugId)
                .orElseThrow(() -> new com.qaas.exception.NotFoundException("Bug not found: " + bugId));
        bug.setStatus(newStatus);
        return BugDtos.BugResponse.from(bugs.save(bug));
    }
}
