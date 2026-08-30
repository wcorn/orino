package ds.project.orino.planner.ledger.importing.dto;

import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 이관 입출력(#1268 · 확정 명세 §12). */
public final class ImportDtos {

    private ImportDtos() {
    }

    /**
     * 1단계 — 파일이 어떻게 생겼는지.
     *
     * @param headers   머리글로 보이는 줄. 매핑 화면이 열 이름을 고르는 데 쓴다
     * @param sample    앞쪽 몇 줄. <b>사람이 눈으로 확인할 근거</b>다
     * @param totalRows 머리글을 뺀 줄 수
     */
    public record AnalyzeResponse(
            List<String> headers,
            List<List<String>> sample,
            int totalRows,
            List<PresetView> presets
    ) {
    }

    /**
     * 열 매핑. 값은 <b>0부터 세는 열 번호</b>다.
     *
     * <p>이름이 아니라 번호인 이유는 머리글이 없는 파일이 있어서다 — 이름을 키로 삼으면
     * 그런 파일은 아예 매핑할 수 없다.
     *
     * @param amount     금액 열. 하나뿐이면 부호나 {@code type}이 방향을 정한다
     * @param inflow     입금 열. 은행 내역은 입금·출금이 <b>두 열</b>로 나뉘어 온다
     * @param outflow    출금 열
     * @param type       유형 열(「지출」·「입금」 같은 말이 든 열). 없으면 부호·입출금 열로 정한다
     * @param asset      자산 열. <b>내보낸 파일을 되돌려 넣을 때 쓴다</b> — 카드사·은행 파일은
     *                   계좌가 하나라 필요 없지만, 백업은 여러 자산이 한 파일에 섞여 있다.
     *                   이름이 안 맞거나 비면 화면에서 고른 자산으로 간다
     */
    public record Mapping(
            @NotNull Integer date,
            Integer amount,
            Integer inflow,
            Integer outflow,
            Integer title,
            Integer memo,
            Integer type,
            Integer category,
            Integer asset
    ) {
        /** 금액을 읽을 길이 하나도 없으면 매핑이 아니다. */
        public boolean hasAmountSource() {
            return amount != null || inflow != null || outflow != null;
        }
    }

    /**
     * 2단계 — 무엇이 들어갈지 미리 본다.
     *
     * @param assetId    기본 자산. 매핑에 자산 열이 없거나 이름이 안 맞는 줄이 여기로 간다 —
     *                   거래는 자산 없이 존재할 수 없다(§3-1)
     * @param skipRows   머리글 등 건너뛸 줄 수
     * @param dateFormat 비우면 흔한 표기를 차례로 시도한다
     */
    public record PreviewRequest(
            @NotNull Long assetId,
            @NotNull Mapping mapping,
            Integer skipRows,
            String dateFormat
    ) {
    }

    /**
     * 미리보기 한 줄.
     *
     * @param rowNumber  파일에서 몇 번째 줄인가. 오류를 파일에서 찾으려면 이 번호가 필요하다
     * @param error      형식 오류 사유. 있으면 이 줄은 넣을 수 없다
     * @param duplicateOf 같아 보이는 기존 거래의 id. <b>자동으로 합치지 않는다</b>(`LDG-092`)
     */
    public record PreviewRow(
            int rowNumber,
            LocalDate occurredOn,
            LedgerFlow type,
            Long amount,
            String title,
            String memo,
            Long categoryId,
            String categoryName,
            String error,
            Long duplicateOf,
            /** 이 줄이 들어갈 자산. 파일의 자산 열이 정했거나, 못 정했으면 기본 자산이다. */
            Long assetId,
            String assetName
    ) {
    }

    /**
     * @param duplicateCount 중복 후보 수. <b>화면이 다시 세지 않는다</b>
     * @param errorCount     형식 오류 줄 수
     */
    public record PreviewResponse(
            List<PreviewRow> rows,
            int totalRows,
            int duplicateCount,
            int errorCount
    ) {
    }

    /**
     * 3단계 — 실행.
     *
     * @param rowNumbers 넣을 줄의 번호. <b>사람이 체크를 해제한 줄은 여기 없다</b> —
     *                   중복을 어떻게 할지 정하는 유일한 방법이 이것이다(병합 API는 없다)
     */
    public record ExecuteRequest(
            @NotNull Long assetId,
            @NotNull Mapping mapping,
            Integer skipRows,
            String dateFormat,
            @NotBlank @Size(max = 60) String source,
            @NotNull List<Integer> rowNumbers
    ) {
    }

    public record ExecuteResponse(Long batchId, int inserted, int skipped) {
    }

    /**
     * @param revertedAt 되돌린 시각. 되돌린 배치도 목록에 남는다 — 그것도 이력이다
     */
    public record BatchView(
            Long id,
            String source,
            String fileName,
            int rowCount,
            int insertedCount,
            Instant createdAt,
            Instant revertedAt
    ) {
    }

    public record RevertResponse(Long batchId, int reverted) {
    }

    public record PresetView(
            Long id,
            String name,
            Mapping mapping,
            int skipRows,
            String dateFormat,
            boolean builtIn
    ) {
    }

    public record PresetSaveRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull Mapping mapping,
            Integer skipRows,
            String dateFormat
    ) {
    }
}
