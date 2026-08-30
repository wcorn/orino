package ds.project.orino.planner.ledger.importing;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerImportPreset;
import ds.project.orino.domain.planner.ledger.repository.LedgerImportPresetRepository;
import ds.project.orino.planner.ledger.importing.dto.ImportDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * 컬럼 매핑 프리셋.
 *
 * <p>같은 카드사 명세서를 매달 다시 매핑하지 않기 위한 것이다. <b>대표 소스 넷을 동봉한다</b> —
 * 처음 쓰는 사람이 빈 화면에서 열 번호를 세는 일부터 시작하면 거기서 그만둔다.
 *
 * <p>동봉 프리셋은 {@code memberId}가 비어 있고 고칠 수도 지울 수도 없다. 내 것으로 바꾸고
 * 싶으면 <b>새로 저장</b>하면 된다 — 남의 기준을 고치는 것보다 내 것을 만드는 편이 안전하다.
 */
@Service
public class LedgerImportPresetService {

    private final LedgerImportPresetRepository presetRepository;
    private final ObjectMapper objectMapper;

    public LedgerImportPresetService(LedgerImportPresetRepository presetRepository,
                                     ObjectMapper objectMapper) {
        this.presetRepository = presetRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ImportDtos.PresetView> list(Long memberId) {
        return presetRepository
                .findAllByMemberIdIsNullOrMemberIdOrderByMemberIdAscNameAsc(memberId).stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public ImportDtos.PresetView create(Long memberId, ImportDtos.PresetSaveRequest request) {
        LedgerImportPreset preset = presetRepository.save(new LedgerImportPreset(
                memberId, request.name(), toJson(request.mapping()),
                request.skipRows() == null ? 1 : request.skipRows(), request.dateFormat()));
        return view(preset);
    }

    @Transactional
    public void delete(Long memberId, Long id) {
        LedgerImportPreset preset = presetRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_IMPORT_PRESET_NOT_FOUND));
        if (preset.isBuiltIn()) {
            throw new CustomException(ErrorCode.LEDGER_IMPORT_PRESET_BUILT_IN);
        }
        presetRepository.delete(preset);
    }

    private ImportDtos.PresetView view(LedgerImportPreset preset) {
        return new ImportDtos.PresetView(preset.getId(), preset.getName(),
                toMapping(preset.getMappingJson()), preset.getSkipRows(),
                preset.getDateFormat(), preset.isBuiltIn());
    }

    private String toJson(ImportDtos.Mapping mapping) {
        return objectMapper.writeValueAsString(mapping);
    }

    private ImportDtos.Mapping toMapping(String json) {
        Map<String, Integer> raw =
                objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() { });
        return new ImportDtos.Mapping(raw.get("date"), raw.get("amount"), raw.get("inflow"),
                raw.get("outflow"), raw.get("title"), raw.get("memo"), raw.get("type"),
                raw.get("category"), raw.get("asset"));
    }
}
