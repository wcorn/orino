package ds.project.orino.planner.preference.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.preference.dto.PreferenceResponse;
import ds.project.orino.planner.preference.dto.PriorityRuleResponse;
import ds.project.orino.planner.preference.dto.UpdatePreferenceRequest;
import ds.project.orino.planner.preference.dto.UpdatePriorityRulesRequest;
import ds.project.orino.planner.preference.service.PreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(
            PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PreferenceResponse>>
    getPreference(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                preferenceService.getPreference(memberId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<PreferenceResponse>>
    updatePreference(
            Authentication authentication,
            @Valid @RequestBody UpdatePreferenceRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                preferenceService.updatePreference(
                        memberId, request)));
    }

    @GetMapping("/priority-rules")
    public ResponseEntity<ApiResponse<List<PriorityRuleResponse>>>
    getPriorityRules(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                preferenceService.getPriorityRules(memberId)));
    }

    @PutMapping("/priority-rules")
    public ResponseEntity<ApiResponse<List<PriorityRuleResponse>>>
    updatePriorityRules(
            Authentication authentication,
            @Valid @RequestBody
            UpdatePriorityRulesRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                preferenceService.updatePriorityRules(
                        memberId, request)));
    }
}
