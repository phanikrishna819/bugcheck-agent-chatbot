package com.bugcheck.agent.api;

import com.bugcheck.agent.api.dto.RegisterWorkspaceRequest;
import com.bugcheck.agent.api.dto.WorkspaceResponse;
import com.bugcheck.agent.workspace.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public List<WorkspaceResponse> list() {
        return workspaceService.list().stream().map(WorkspaceResponse::new).toList();
    }

    @PostMapping
    public WorkspaceResponse register(@Valid @RequestBody RegisterWorkspaceRequest request) {
        return new WorkspaceResponse(workspaceService.register(request.path()));
    }
}
