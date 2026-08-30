package ds.project.orino.planner.ledger.importing;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.ledger.importing.dto.ImportDtos;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 이관 API(#1268 · 확정 명세 §12).
 *
 * <p><b>병합 엔드포인트가 없다.</b> 중복 후보는 미리보기가 보여줄 뿐이고, 처리 방법은
 * 실행 목록에서 그 줄을 빼는 것 하나뿐이다(`LDG-092`) — 자동 병합의 불투명함이 원장 신뢰를
 * 깨뜨린다는 것이 벤치마크의 교훈이다.
 */
@RestController
@RequestMapping("/api/ledger")
public class LedgerImportController {

    private final LedgerImportService importService;
    private final LedgerImportPresetService presetService;
    private final LedgerExportService exportService;

    public LedgerImportController(LedgerImportService importService,
                                  LedgerImportPresetService presetService,
                                  LedgerExportService exportService) {
        this.importService = importService;
        this.presetService = presetService;
        this.exportService = exportService;
    }

    @PostMapping(value = "/import/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportDtos.AnalyzeResponse> analyze(
            @AuthenticationPrincipal Long memberId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(importService.analyze(memberId, file));
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportDtos.PreviewResponse> preview(
            @AuthenticationPrincipal Long memberId,
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("request") ImportDtos.PreviewRequest request) {
        return ApiResponse.success(importService.preview(memberId, file, request));
    }

    @PostMapping(value = "/import/execute", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportDtos.ExecuteResponse> execute(
            @AuthenticationPrincipal Long memberId,
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("request") ImportDtos.ExecuteRequest request) {
        return ApiResponse.success(importService.execute(memberId, file, request));
    }

    @GetMapping("/import/batches")
    public ApiResponse<List<ImportDtos.BatchView>> batches(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(importService.batches(memberId));
    }

    @PostMapping("/import/batches/{id}/revert")
    public ApiResponse<ImportDtos.RevertResponse> revert(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        return ApiResponse.success(importService.revert(memberId, id));
    }

    @GetMapping("/import/presets")
    public ApiResponse<List<ImportDtos.PresetView>> presets(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(presetService.list(memberId));
    }

    @PostMapping("/import/presets")
    public ApiResponse<ImportDtos.PresetView> createPreset(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ImportDtos.PresetSaveRequest request) {
        return ApiResponse.success(presetService.create(memberId, request));
    }

    @DeleteMapping("/import/presets/{id}")
    public ApiResponse<Void> deletePreset(@AuthenticationPrincipal Long memberId,
                                          @PathVariable Long id) {
        presetService.delete(memberId, id);
        return ApiResponse.success(null);
    }

    /**
     * 내보내기(`LDG-094`).
     *
     * <p>파일로 내려보내므로 성공 봉투를 쓰지 않는다 — 봉투에 담으면 브라우저가 파일로
     * 저장하지 못한다.
     */
    @GetMapping("/export")
    public ResponseEntity<Resource> export(@AuthenticationPrincipal Long memberId,
                                           @RequestParam LocalDate from,
                                           @RequestParam LocalDate to,
                                           @RequestParam(defaultValue = "csv") String format) {
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        byte[] body = xlsx
                ? exportService.toXlsx(memberId, from, to)
                : exportService.toCsv(memberId, from, to);
        String fileName = "orino-ledger-%s_%s.%s".formatted(from, to, xlsx ? "xlsx" : "csv");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .contentType(xlsx
                        ? MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        : MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(new ByteArrayResource(body));
    }
}
