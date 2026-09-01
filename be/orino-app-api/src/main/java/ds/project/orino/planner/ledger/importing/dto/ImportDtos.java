package ds.project.orino.planner.ledger.importing.dto;

import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
     * @param sample    머리글 다음 몇 줄. <b>사람이 눈으로 확인할 근거</b>다
     * @param totalRows 머리글과 그 앞을 뺀 줄 수
     * @param headerRow 머리글이 몇 번째 줄이었는지(0부터). 화면의 「건너뛸 줄 수」가 이 값 + 1이다
     */
    public record AnalyzeResponse(
            List<String> headers,
            List<List<String>> sample,
            int totalRows,
            int headerRow,
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
     * 파일 한 장을 어떻게 읽을지(#1320).
     *
     * <p><b>파일마다 따로 온다.</b> 은행 내역과 카드 명세서를 함께 올릴 수 있어야 하고, 그 둘은
     * 열 구성도 들어갈 자산도 다르다 — 하나로 묶으면 섞어 올리는 길이 막힌다.
     *
     * @param assetId    기본 자산. 매핑에 자산 열이 없거나 이름이 안 맞는 줄이 여기로 간다 —
     *                   거래는 자산 없이 존재할 수 없다(§3-1)
     * @param skipRows   머리글 등 건너뛸 줄 수
     * @param dateFormat 비우면 흔한 표기를 차례로 시도한다
     * @param password   암호가 걸린 xlsx의 비밀번호. <b>저장하지 않는다</b> — 파일을 단계마다
     *                   다시 올리므로 비밀번호도 그때마다 함께 온다
     */
    /**
     * 파일 한 장을 읽는 데 필요한 것. 미리보기와 실행이 <b>같은 값으로 읽어야</b> 화면에서
     * 본 것과 들어간 것이 같다 — 두 벌로 두면 언젠가 한쪽만 고쳐진다.
     */
    public sealed interface FileRead permits FileMapping, FileExecute {
        Long assetId();

        Mapping mapping();

        Integer skipRows();

        String dateFormat();

        String password();
    }

    public record FileMapping(
            @NotNull Long assetId,
            @NotNull Mapping mapping,
            Integer skipRows,
            String dateFormat,
            String password
    ) implements FileRead {
    }

    /**
     * 2단계 — 무엇이 들어갈지 미리 본다.
     *
     * <p><b>파일 목록과 설정 목록은 순서로 짝을 짓는다.</b> 한 요청에 다 와야 파일끼리 겹치는
     * 줄을 볼 수 있다 — 파일마다 따로 물으면 두 번째 파일을 볼 때 첫 파일은 아직 어디에도
     * 없어서, 겹치는 구간이 「중복 없음」으로 지나간다.
     */
    public record PreviewRequest(
            @NotEmpty List<@Valid @NotNull FileMapping> files
    ) {
    }

    /**
     * 같은 실행 안 <b>앞 파일</b>의 어느 줄을 가리킨다(#1320).
     *
     * <p>기존 거래는 id가 있지만 이 줄들은 아직 아무 데도 없다 — 그래서 id가 아니라 자리로
     * 가리킨다. 「1번 파일 12번째 줄」이라고 말할 수 있어야 사람이 그 줄을 찾아 판단한다.
     */
    public record RowRef(int fileIndex, int rowNumber) {
    }

    /**
     * 미리보기 한 줄.
     *
     * @param rowNumber  파일에서 몇 번째 줄인가. 오류를 파일에서 찾으려면 이 번호가 필요하다
     * @param error      형식 오류 사유. 있으면 이 줄은 넣을 수 없다
     * @param duplicateOf 같아 보이는 기존 거래의 id. <b>자동으로 합치지 않는다</b>(`LDG-092`)
     * @param duplicateOfRow 같아 보이는 <b>앞 파일의 줄</b>. 기간이 겹치게 내려받은 파일을 함께
     *                   올렸을 때 걸린다. {@code duplicateOf}가 있으면 비어 있다 — 이미 원장에
     *                   있는 거래를 가리키는 편이 구체적이다
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
            RowRef duplicateOfRow,
            /** 이 줄이 들어갈 자산. 파일의 자산 열이 정했거나, 못 정했으면 기본 자산이다. */
            Long assetId,
            String assetName
    ) {
    }

    /**
     * 파일 한 장의 미리보기.
     *
     * <p>파일 경계를 살려 내려보낸다. 줄 번호는 <b>파일 안에서</b> 세므로, 합쳐 놓으면 3번 줄이
     * 여러 개가 되어 어느 파일의 3번인지 말할 수 없다.
     */
    public record FilePreview(
            int fileIndex,
            String fileName,
            List<PreviewRow> rows,
            int totalRows,
            int duplicateCount,
            int errorCount
    ) {
    }

    /**
     * 합계가 위에 있다 — <b>화면이 다시 세지 않는다.</b> 파일별 내역은 {@code files}에 있다.
     *
     * @param duplicateCount 중복 후보 수(기존 거래와 겹친 줄 + 앞 파일과 겹친 줄)
     * @param errorCount     형식 오류 줄 수
     */
    public record PreviewResponse(
            List<FilePreview> files,
            int totalRows,
            int duplicateCount,
            int errorCount
    ) {
    }

    /**
     * 실행할 파일 한 장.
     *
     * @param source     이 파일로 만들 배치의 이름. 배치는 <b>파일마다 하나씩</b> 생긴다 —
     *                   아홉 장 중 한 장만 잘못 넣었을 때 나머지 여덟 장이 살아야 한다
     * @param rowNumbers 넣을 줄의 번호. <b>사람이 체크를 해제한 줄은 여기 없다</b> —
     *                   중복을 어떻게 할지 정하는 유일한 방법이 이것이다(병합 API는 없다)
     */
    public record FileExecute(
            @NotNull Long assetId,
            @NotNull Mapping mapping,
            Integer skipRows,
            String dateFormat,
            String password,
            @NotBlank @Size(max = 60) String source,
            @NotNull List<Integer> rowNumbers
    ) implements FileRead {
    }

    /** 3단계 — 실행. 파일 목록과 순서로 짝을 짓는다. */
    public record ExecuteRequest(
            @NotEmpty List<@Valid @NotNull FileExecute> files
    ) {
    }

    /** 파일 한 장이 만든 배치. 되돌리기가 <b>파일 단위</b>로 남는다. */
    public record BatchResult(Long batchId, String fileName, int inserted, int skipped) {
    }

    /** @param inserted 전체 합계. 파일별 내역은 {@code batches}에 있다 */
    public record ExecuteResponse(List<BatchResult> batches, int inserted, int skipped) {
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
