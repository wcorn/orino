package ds.project.orino.planner.ledger.importing;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerAutoRule;
import ds.project.orino.domain.planner.ledger.entity.LedgerCategory;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerImportBatch;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerCategoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerImportBatchRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.common.LedgerBootstrap;
import ds.project.orino.planner.ledger.common.LedgerClock;
import ds.project.orino.planner.ledger.importing.dto.ImportDtos;
import ds.project.orino.planner.ledger.rule.LedgerAutoRuleService;
import ds.project.orino.planner.ledger.transaction.LedgerTransactionService;
import ds.project.orino.planner.ledger.transaction.dto.TransactionCreateRequest;
import ds.project.orino.planner.ledger.transaction.dto.TransactionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 파일에서 원장으로(`LDG-091`~`LDG-093` · 확정 명세 §12).
 *
 * <p><b>수동 입력을 대체하지 않는다.</b> 초기 이관과 월말 대사를 위한 도구다 —
 * 「소스·파일 → 컬럼 매핑 → 미리보기·검증 → 실행」 넷으로 나뉘어 있고, 각 단계가 사람에게
 * 무언가를 <b>보여준 뒤</b> 다음으로 넘어간다.
 *
 * <p><b>파일을 서버에 두지 않는다.</b> 단계마다 다시 올린다 — 임시 저장소를 두면 「언제
 * 지우나」와 「누구 것인가」가 따라오고, 그 비용이 한 번 더 올리는 불편보다 크다.
 *
 * <p><b>자동으로 병합하지 않는다</b>(`LDG-092`). 중복 후보는 <b>보여줄 뿐</b>이고,
 * 병합 API는 만들지 않았다 — 사람이 실행 목록에서 그 줄을 빼는 것이 유일한 처리다.
 * 자동 병합의 불투명함이 원장 신뢰를 깨뜨린다.
 */
@Service
public class LedgerImportService {

    /** 중복을 견줄 때 앞뒤로 볼 날. 같은 거래가 하루 어긋나 적히는 소스가 있다. */
    private static final int DUPLICATE_WINDOW_DAYS = 1;

    private final LedgerSheetReader reader;
    private final LedgerTransactionService transactionService;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerImportBatchRepository batchRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final LedgerAssetRepository assetRepository;
    private final LedgerAutoRuleService autoRuleService;
    private final LedgerImportPresetService presetService;
    private final LedgerBootstrap bootstrap;
    private final LedgerClock clock;

    public LedgerImportService(LedgerSheetReader reader,
                               LedgerTransactionService transactionService,
                               LedgerTransactionRepository transactionRepository,
                               LedgerImportBatchRepository batchRepository,
                               LedgerCategoryRepository categoryRepository,
                               LedgerAssetRepository assetRepository,
                               LedgerAutoRuleService autoRuleService,
                               LedgerImportPresetService presetService,
                               LedgerBootstrap bootstrap,
                               LedgerClock clock) {
        this.reader = reader;
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.batchRepository = batchRepository;
        this.categoryRepository = categoryRepository;
        this.assetRepository = assetRepository;
        this.autoRuleService = autoRuleService;
        this.presetService = presetService;
        this.bootstrap = bootstrap;
        this.clock = clock;
    }

    /**
     * 1단계 — 파일의 생김새와 쓸 수 있는 프리셋. 아직 아무것도 해석하지 않는다.
     *
     * <p><b>머리글이 1행이라고 못 박지 않는다</b>(#1318). 은행 파일은 앞에 제목·계좌번호·
     * 주의사항이 붙어 오고, 그걸 머리글로 읽으면 화면의 열 이름이 전부 「(이름 없음)」이 된다.
     * 찾은 줄 번호를 함께 돌려주어 <b>「건너뛸 머리글 줄 수」가 저절로 채워지게</b> 한다.
     */
    @Transactional(readOnly = true)
    public ImportDtos.AnalyzeResponse analyze(Long memberId, MultipartFile file, String password) {
        List<List<String>> rows = reader.read(file, password);
        int headerRow = LedgerHeaderFinder.find(rows);
        List<String> headers = rows.get(headerRow);
        // 머리글 다음 다섯 줄. 더 보여줘도 사람이 읽지 않고, 적으면 확인이 안 된다.
        List<List<String>> sample =
                rows.subList(headerRow + 1, Math.min(rows.size(), headerRow + 6));
        return new ImportDtos.AnalyzeResponse(
                headers, List.copyOf(sample), rows.size() - headerRow - 1, headerRow,
                presetService.list(memberId));
    }

    /**
     * 2단계 — 무엇이 들어갈지 보여준다.
     *
     * <p>세 가지를 한 번에 알린다: <b>형식 오류</b>(넣을 수 없는 줄) · <b>중복 후보</b>(같아
     * 보이는 기존 거래) · <b>자동 분류 결과</b>. 셋 다 실행 전에 보여야 한다 — 들어간 뒤에
     * 알면 배치를 통째로 되돌리는 수밖에 없다.
     */
    @Transactional(readOnly = true)
    public ImportDtos.PreviewResponse preview(Long memberId, MultipartFile file,
                                              ImportDtos.PreviewRequest request) {
        List<ParsedRow> parsed = parse(memberId, file, request.mapping(),
                request.skipRows(), request.dateFormat(), request.password());

        Map<Long, String> categoryNames = categoryNames(memberId);
        Map<Long, String> assetNames = assetNames(memberId);
        List<LedgerTransaction> existing = candidatesFor(memberId, parsed);

        List<ImportDtos.PreviewRow> rows = new ArrayList<>();
        int duplicates = 0;
        int errors = 0;
        for (ParsedRow row : parsed) {
            Long assetId = row.assetId == null ? request.assetId() : row.assetId;
            Long duplicateOf = row.error != null ? null : duplicateOf(row, existing, assetId);
            if (row.error != null) {
                errors++;
            } else if (duplicateOf != null) {
                duplicates++;
            }
            rows.add(new ImportDtos.PreviewRow(
                    row.rowNumber, row.occurredOn, row.type, row.amount, row.title, row.memo,
                    row.categoryId, categoryNames.get(row.categoryId), row.error, duplicateOf,
                    assetId, assetNames.get(assetId)));
        }
        return new ImportDtos.PreviewResponse(rows, rows.size(), duplicates, errors);
    }

    /**
     * 3단계 — 실행.
     *
     * <p>넣을 줄은 <b>요청이 정한다.</b> 중복 후보든 아니든 여기서 다시 거르지 않는다 —
     * 미리보기에서 사람이 본 것과 실제로 들어가는 것이 다르면, 그 순간 미리보기는 거짓말이 된다.
     *
     * <p>거래 생성은 {@link LedgerTransactionService}를 그대로 지나간다. 카드 사이클 편입·
     * 예정 판정·검증이 <b>손으로 적을 때와 같아야</b> 하기 때문이다 — 여기서 따로 만들면
     * 가져온 거래만 청구서에 안 잡히는 날이 온다.
     */
    @Transactional
    public ImportDtos.ExecuteResponse execute(Long memberId, MultipartFile file,
                                              ImportDtos.ExecuteRequest request) {
        bootstrap.ensureSeeded(memberId);
        List<ParsedRow> parsed = parse(memberId, file, request.mapping(),
                request.skipRows(), request.dateFormat(), request.password());
        Set<Integer> wanted = new HashSet<>(request.rowNumbers());

        List<TransactionCreateRequest> requests = new ArrayList<>();
        for (ParsedRow row : parsed) {
            if (row.error != null || !wanted.contains(row.rowNumber)) {
                continue;
            }
            requests.add(new TransactionCreateRequest(
                    row.type, row.amount, row.occurredOn, null,
                    row.assetId == null ? request.assetId() : row.assetId, null,
                    row.categoryId, row.title, row.memo, List.of(), false, null, null));
        }

        LedgerImportBatch batch = batchRepository.save(new LedgerImportBatch(
                memberId, request.source(), file.getOriginalFilename(), parsed.size()));

        List<TransactionView> created = requests.isEmpty()
                ? List.of()
                : transactionService.createAll(memberId, requests).created();
        stampBatch(created, batch.getId());
        batch.markInserted(created.size());

        return new ImportDtos.ExecuteResponse(
                batch.getId(), created.size(), parsed.size() - created.size());
    }

    @Transactional(readOnly = true)
    public List<ImportDtos.BatchView> batches(Long memberId) {
        return batchRepository.findAllByMemberIdOrderByCreatedAtDescIdDesc(memberId).stream()
                .map(batch -> new ImportDtos.BatchView(
                        batch.getId(), batch.getSource(), batch.getFileName(),
                        batch.getRowCount(), batch.getInsertedCount(),
                        batch.getCreatedAt(), batch.getRevertedAt()))
                .toList();
    }

    /**
     * 배치 되돌리기(`LDG-093`).
     *
     * <p><b>그 배치로 들어온 행만</b> 소프트 삭제한다. 손으로 적은 줄은 {@code importBatchId}가
     * 비어 있어 걸리지 않는다 — 함께 지워지면 그건 복구가 아니라 사고다.
     *
     * <p>두 번 되돌리는 것은 <b>거부한다.</b> 두 번째는 아무 일도 안 하는데 화면은 성공으로
     * 읽고, 「되돌렸다」는 같은 말이 두 뜻을 갖게 된다.
     */
    @Transactional
    public ImportDtos.RevertResponse revert(Long memberId, Long batchId) {
        LedgerImportBatch batch = batchRepository.findByIdAndMemberId(batchId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_IMPORT_BATCH_NOT_FOUND));
        if (batch.isReverted()) {
            throw new CustomException(ErrorCode.LEDGER_IMPORT_ALREADY_REVERTED);
        }

        List<LedgerTransaction> rows =
                transactionRepository.findAllByImportBatchIdAndDeletedAtIsNull(batchId);
        rows.forEach(tx -> tx.softDelete(clock.now()));
        batch.revert(clock.now());
        return new ImportDtos.RevertResponse(batchId, rows.size());
    }

    /**
     * 파일 한 장을 줄 목록으로.
     *
     * <p>못 읽은 줄도 <b>버리지 않고</b> 사유를 달아 남긴다 — 조용히 빠지면 사람은 전부
     * 들어갔다고 믿고, 빠진 줄은 몇 달 뒤 잔액이 안 맞을 때에야 드러난다.
     */
    private List<ParsedRow> parse(Long memberId, MultipartFile file, ImportDtos.Mapping mapping,
                                  Integer skipRows, String dateFormat, String password) {
        if (mapping.date() == null || !mapping.hasAmountSource()) {
            throw new CustomException(ErrorCode.LEDGER_IMPORT_MAPPING_REQUIRED);
        }
        List<List<String>> rows = reader.read(file, password);
        int skip = skipRows == null ? 1 : Math.max(skipRows, 0);
        List<LedgerAutoRule> rules = autoRuleService.rulesOf(memberId);
        Map<String, Long> categoryByName = categoryIdsByName(memberId);
        Map<String, Long> assetByName = assetIdsByName(memberId);

        List<ParsedRow> parsed = new ArrayList<>();
        for (int i = skip; i < rows.size(); i++) {
            parsed.add(parseRow(rows.get(i), i + 1, mapping, dateFormat, rules,
                    categoryByName, assetByName));
        }
        return parsed;
    }

    private ParsedRow parseRow(List<String> cells, int rowNumber, ImportDtos.Mapping mapping,
                               String dateFormat, List<LedgerAutoRule> rules,
                               Map<String, Long> categoryByName,
                               Map<String, Long> assetByName) {
        ParsedRow row = new ParsedRow();
        row.rowNumber = rowNumber;
        /*
         * 내용을 먼저 채운다. 못 읽은 줄에도 <b>파일에 적힌 말</b>이 보여야 그 줄을 파일에서
         * 찾을 수 있다 — 「제목 없음」만 남으면 몇 번째 줄인지로만 뒤져야 한다.
         */
        row.title = cellAt(cells, mapping.title());
        row.memo = cellAt(cells, mapping.memo());

        row.occurredOn = LedgerDateParser.parse(cellAt(cells, mapping.date()), dateFormat);
        if (row.occurredOn == null) {
            row.error = "날짜를 읽을 수 없습니다";
            return row;
        }

        Signed money = amountOf(cells, mapping);
        if (money == null) {
            row.error = "금액을 읽을 수 없습니다";
            return row;
        }
        if (money.amount == 0) {
            // 0원 줄은 소스의 요약·합계 행인 경우가 많다. 넣으면 통계에 빈 줄이 쌓인다.
            row.error = "금액이 0원입니다";
            return row;
        }
        row.amount = money.amount;
        row.type = typeOf(cells, mapping, money);

        // 파일에 카테고리 열이 있으면 그 이름을 먼저 쓰고, 없을 때만 규칙이 채운다 —
        // 파일이 말한 것이 규칙보다 구체적이다.
        Long fromFile = categoryByName.get(normalize(cellAt(cells, mapping.category())));
        row.categoryId = fromFile != null
                ? fromFile
                : autoRuleService.classify(rules, row.title, null);

        // 자산 열이 있으면 이름으로 찾는다. 못 찾으면 null로 두고 기본 자산이 받는다 —
        // 여기서 거부하면 이름 하나 다른 것 때문에 백업 복원이 통째로 멈춘다.
        row.assetId = assetByName.get(normalize(cellAt(cells, mapping.asset())));
        return row;
    }

    /**
     * 금액과 방향.
     *
     * <p>은행 내역은 <b>입금·출금이 두 열</b>로 나뉘어 오고, 카드 명세서는 한 열에 부호로 온다.
     * 둘 다 받는다 — 소스에 맞추라고 요구하면 이관을 포기하게 된다.
     */
    private Signed amountOf(List<String> cells, ImportDtos.Mapping mapping) {
        Long inflow = number(cellAt(cells, mapping.inflow()));
        Long outflow = number(cellAt(cells, mapping.outflow()));
        if (inflow != null && inflow != 0) {
            return new Signed(inflow, LedgerFlow.INCOME);
        }
        if (outflow != null && outflow != 0) {
            return new Signed(outflow, LedgerFlow.EXPENSE);
        }
        if (mapping.amount() == null) {
            // 입출금 열만 있고 둘 다 비었다 — 0원 줄로 본다.
            return inflow == null && outflow == null ? null : new Signed(0, LedgerFlow.EXPENSE);
        }
        String raw = cellAt(cells, mapping.amount());
        Long value = number(raw);
        if (value == null) {
            return null;
        }
        // 원장의 amount는 언제나 양수다. 부호는 방향이지 크기가 아니다.
        boolean negative = raw != null && raw.trim().startsWith("-");
        return new Signed(Math.abs(value), negative ? LedgerFlow.EXPENSE : LedgerFlow.INCOME);
    }

    /**
     * 유형 열이 있으면 그것이 이긴다.
     *
     * <p>「지출」이라 적힌 줄을 부호만 보고 수입으로 넣으면, 사람은 파일이 그렇게 말했다고
     * 믿기 때문에 틀린 것을 찾지 못한다.
     */
    private LedgerFlow typeOf(List<String> cells, ImportDtos.Mapping mapping, Signed money) {
        String raw = cellAt(cells, mapping.type());
        if (raw == null || raw.isBlank()) {
            return money.type;
        }
        String value = raw.trim();
        if (value.contains("입금") || value.contains("수입") || value.equalsIgnoreCase("income")) {
            return LedgerFlow.INCOME;
        }
        if (value.contains("출금") || value.contains("지출") || value.contains("승인")
                || value.equalsIgnoreCase("expense")) {
            return LedgerFlow.EXPENSE;
        }
        return money.type;
    }

    /**
     * 중복 후보 찾기(`LDG-092`).
     *
     * <p>날짜(±1일) + 금액 + 자산이 같고 <b>내용이 비슷하면</b> 후보다. 내용 비교는 공백·
     * 대소문자를 지우고 견준다 — 「스타벅스 역삼」과 「스타벅스역삼」은 같은 거래다.
     *
     * <p>내용이 양쪽 다 비어 있으면 날짜·금액·자산만으로 후보로 본다. 같은 날 같은 금액을
     * 같은 자산에서 두 번 쓰는 일은 드물고, <b>드문 것을 보여주는 비용이 놓치는 비용보다 싸다.</b>
     */
    private Long duplicateOf(ParsedRow row, List<LedgerTransaction> existing, Long assetId) {
        String title = normalize(row.title);
        for (LedgerTransaction tx : existing) {
            if (!assetId.equals(tx.getAssetId()) || tx.getAmount() != row.amount) {
                continue;
            }
            long gap = Math.abs(tx.getOccurredOn().toEpochDay() - row.occurredOn.toEpochDay());
            if (gap > DUPLICATE_WINDOW_DAYS) {
                continue;
            }
            String other = normalize(tx.getTitle());
            if (title.isEmpty() || other.isEmpty() || title.equals(other)
                    || title.contains(other) || other.contains(title)) {
                return tx.getId();
            }
        }
        return null;
    }

    /** 견줄 구간을 파일이 정한다 — 원장 전체를 읽으면 몇 년치 이관에서 메모리가 터진다. */
    private List<LedgerTransaction> candidatesFor(Long memberId, List<ParsedRow> parsed) {
        LocalDate from = null;
        LocalDate to = null;
        for (ParsedRow row : parsed) {
            if (row.occurredOn == null) {
                continue;
            }
            from = from == null || row.occurredOn.isBefore(from) ? row.occurredOn : from;
            to = to == null || row.occurredOn.isAfter(to) ? row.occurredOn : to;
        }
        if (from == null) {
            return List.of();
        }
        return transactionRepository.findDuplicateCandidates(memberId,
                from.minusDays(DUPLICATE_WINDOW_DAYS), to.plusDays(DUPLICATE_WINDOW_DAYS));
    }

    private void stampBatch(List<TransactionView> created, Long batchId) {
        if (created.isEmpty()) {
            return;
        }
        List<Long> ids = created.stream().map(TransactionView::id).toList();
        transactionRepository.findAllById(ids)
                .forEach(tx -> tx.attachToImportBatch(batchId));
    }

    private Map<Long, String> categoryNames(Long memberId) {
        Map<Long, String> names = new HashMap<>();
        categoryRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId)
                .forEach(category -> names.put(category.getId(), category.getName()));
        return names;
    }

    private Map<Long, String> assetNames(Long memberId) {
        Map<Long, String> names = new HashMap<>();
        assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId)
                .forEach(asset -> names.put(asset.getId(), asset.getName()));
        return names;
    }

    /** 이름이 겹치면 먼저 만든 자산이 이긴다 — 뒤엣것이 이기면 순서가 결과를 바꾼다. */
    private Map<String, Long> assetIdsByName(Long memberId) {
        Map<String, Long> ids = new HashMap<>();
        assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId)
                .forEach(asset -> ids.putIfAbsent(normalize(asset.getName()), asset.getId()));
        return ids;
    }

    private Map<String, Long> categoryIdsByName(Long memberId) {
        Map<String, Long> ids = new HashMap<>();
        for (LedgerCategory category
                : categoryRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId)) {
            if (category.getFlow() == LedgerFlow.EXPENSE) {
                ids.putIfAbsent(normalize(category.getName()), category.getId());
            }
        }
        return ids;
    }

    private static String cellAt(List<String> cells, Integer index) {
        if (index == null || index < 0 || index >= cells.size()) {
            return null;
        }
        String value = cells.get(index);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 「1,234원」·「₩1,234」·「1 234」를 모두 1234로. 소스마다 꾸밈이 다르다. */
    private static Long number(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9.-]", "");
        if (digits.isBlank() || digits.equals("-") || digits.equals(".")) {
            return null;
        }
        try {
            // 소수점이 있으면 반올림한다 — 원 단위 아래는 원장이 갖지 않는다.
            return Math.round(Double.parseDouble(digits));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }

    /** 파일 한 줄을 해석한 결과. 오류도 결과다 — 버리지 않는다. */
    private static final class ParsedRow {
        private int rowNumber;
        private LocalDate occurredOn;
        private LedgerFlow type;
        /** 못 읽었으면 {@code null}이다 — 0으로 두면 화면이 「0원짜리 줄」이라고 말한다. */
        private Long amount;
        private String title;
        private String memo;
        private Long categoryId;
        /** 파일이 정한 자산. {@code null}이면 화면에서 고른 기본 자산으로 간다. */
        private Long assetId;
        private String error;
    }

    private record Signed(long amount, LedgerFlow type) {
    }
}
