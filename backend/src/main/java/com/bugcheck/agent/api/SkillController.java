package com.bugcheck.agent.api;

import com.bugcheck.agent.asset.AgentAssetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skills")
public class SkillController {
    private final AgentAssetService assetService;

    public SkillController(AgentAssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("assetsRoot", assetService.assetsRoot().toString(), "skills", List.copyOf(assetService.listSkillNames()));
    }
}
