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
 *
 * <p><b>파일을 여러 장 받는다</b>(#1320). 은행이 내려주는 거래내역은 한 장이 아니다 — 기간을
 * 나눠 받아야 하고, 그렇게 받으면 <b>구간이 겹친다.</b> 그래서 중복 후보를 원장뿐 아니라
 * <b>앞 파일의 줄</b>과도 견준다. 견주지 않으면 겹치는 구간이 조용히 두 번 들어가는데,
 * 미리보기는 그 직전에 「중복 없음」이라고 말한 뒤다 — `LDG-092`가 막으려던 바로 그 일이다.
 *
 * <p>다만 <b>같은 파일 안</b>의 줄끼리는 견주지 않는다. 한 파일은 은행이 준 그대로이고, 그
 * 안에 같은 줄이 두 번 있으면 실제로 두 번 일어난 거래다. 겹침은 「같은 기간을 두 번
 * 내려받았을 때」 생기고, 그것은 파일 경계에서 생긴다.
 */
@Service
public class LedgerImportService {

    /** 중복을 견줄 때 앞뒤로 볼 날. 같은 거래가 하루 어긋나 적히는 소스가 있다. */
    private static final int DUPLICATE_WINDOW_DAYS = 1;

    /**
     * 한 번에 받을 파일 수.
     *
     * <p>줄 수 상한({@link LedgerSheetReader#MAX_ROWS})만으로는 부족하다 — 파일을 전부 메모리에
     * 들고 파싱하므로, 20줄짜리 파일 천 장도 같은 곳에 닿는다. 아홉 해치를 아홉 장으로 받는
     * 것이 실제 쓰임이라 스무 장이면 넉넉하다.
     */
    private static final int MAX_FILES = 20;

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
    public ImportDtos.PreviewResponse preview(Long memberId, List<MultipartFile> files,
                                              ImportDtos.PreviewRequest request) {
        List<List<ParsedRow>> byFile = parseAll(memberId, files, request.files());

        Map<Long, String> categoryNames = categoryNames(memberId);
        Map<Long, String> assetNames = assetNames(memberId);
        List<LedgerTransaction> existing = candidatesFor(memberId,
                byFile.stream().flatMap(List::stream).toList());

        // 앞 파일들이 넣으려는 줄. 파일 하나를 다 훑은 뒤에 넣는다 — 같은 파일 안끼리는
        // 견주지 않기 때문이다.
        List<PriorRow> prior = new ArrayList<>();
        List<ImportDtos.FilePreview> previews = new ArrayList<>();
        int totalRows = 0;
        int totalDuplicates = 0;
        int totalErrors = 0;

        for (int fileIndex = 0; fileIndex < byFile.size(); fileIndex++) {
            Long defaultAssetId = request.files().get(fileIndex).assetId();
            List<ParsedRow> parsed = byFile.get(fileIndex);
            List<ImportDtos.PreviewRow> rows = new ArrayList<>();
            List<PriorRow> fromThisFile = new ArrayList<>();
            int duplicates = 0;
            int errors = 0;

            for (ParsedRow row : parsed) {
                Long assetId = row.assetId == null ? defaultAssetId : row.assetId;
                Long duplicateOf = null;
                ImportDtos.RowRef duplicateOfRow = null;
                if (row.error != null) {
                    errors++;
                } else {
                    duplicateOf = duplicateOf(row, existing, assetId);
                    // 원장에 이미 있는 거래가 먼저다 — 그쪽이 더 구체적이고, 사람이 열어
                    // 확인할 수 있다. 앞 파일의 줄은 아직 아무 데도 없다.
                    if (duplicateOf == null) {
                        duplicateOfRow = duplicateInPriorFiles(row, prior, assetId);
                    }
                    if (duplicateOf != null || duplicateOfRow != null) {
                        duplicates++;
                    }
                    fromThisFile.add(new PriorRow(fileIndex, row.rowNumber, row.occurredOn,
                            row.amount, assetId, normalize(row.title)));
                }
                rows.add(new ImportDtos.PreviewRow(
                        row.rowNumber, row.occurredOn, row.type, row.amount, row.title, row.memo,
                        row.categoryId, categoryNames.get(row.categoryId), row.error, duplicateOf,
                        duplicateOfRow, assetId, assetNames.get(assetId)));
            }

            prior.addAll(fromThisFile);
            previews.add(new ImportDtos.FilePreview(fileIndex,
                    files.get(fileIndex).getOriginalFilename(), rows, rows.size(),
                    duplicates, errors));
            totalRows += rows.size();
            totalDuplicates += duplicates;
            totalErrors += errors;
        }
        return new ImportDtos.PreviewResponse(previews, totalRows, totalDuplicates, totalErrors);
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
     *
     * <p><b>배치는 파일마다 하나</b>다(#1320). 되돌리기가 파일 단위로 남아야 아홉 장 중 한 장만
     * 물릴 수 있다. 그러면서도 <b>한 트랜잭션</b>이라, 도중에 실패하면 그때까지 만든 배치까지
     * 전부 사라진다 — 절반만 들어간 상태로 끝나면 무엇을 다시 올려야 하는지 알 수 없다.
     */
    @Transactional
    public ImportDtos.ExecuteResponse execute(Long memberId, List<MultipartFile> files,
                                              ImportDtos.ExecuteRequest request) {
        bootstrap.ensureSeeded(memberId);
        // 넣기 전에 전부 읽는다 — 다섯 장째가 안 읽히는 것을 넉 장을 넣은 뒤에 알면,
        // 되돌릴 배치가 이미 넷이다.
        List<List<ParsedRow>> byFile = parseAll(memberId, files, request.files());

        List<ImportDtos.BatchResult> batches = new ArrayList<>();
        int totalInserted = 0;
        int totalSkipped = 0;

        for (int fileIndex = 0; fileIndex < byFile.size(); fileIndex++) {
            ImportDtos.FileExecute spec = request.files().get(fileIndex);
            List<ParsedRow> parsed = byFile.get(fileIndex);
            Set<Integer> wanted = new HashSet<>(spec.rowNumbers());

            List<TransactionCreateRequest> requests = new ArrayList<>();
            for (ParsedRow row : parsed) {
                if (row.error != null || !wanted.contains(row.rowNumber)) {
                    continue;
                }
                // 마지막 null은 여행이다. 은행·카드 파일에는 그런 정보가 없으므로 붙이지
                // 않는다 — 넣은 뒤 기간으로 걸러 한 번에 붙이는 길이 따로 있다(§18).
                requests.add(new TransactionCreateRequest(
                        row.type, row.amount, row.occurredOn, null,
                        row.assetId == null ? spec.assetId() : row.assetId, null,
                        row.categoryId, row.title, row.memo, List.of(), false, null, null, null));
            }

            String fileName = files.get(fileIndex).getOriginalFilename();
            LedgerImportBatch batch = batchRepository.save(new LedgerImportBatch(
                    memberId, spec.source(), fileName, parsed.size()));

            List<TransactionView> created = requests.isEmpty()
                    ? List.of()
                    : transactionService.createAll(memberId, requests).created();
            stampBatch(created, batch.getId());
            batch.markInserted(created.size());

            int skipped = parsed.size() - created.size();
            batches.add(new ImportDtos.BatchResult(
                    batch.getId(), fileName, created.size(), skipped));
            totalInserted += created.size();
            totalSkipped += skipped;
        }
        return new ImportDtos.ExecuteResponse(batches, totalInserted, totalSkipped);
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
     * 파일 전부를 줄 목록으로. <b>파일마다 제 설정으로</b> 읽는다.
     *
     * <p>못 읽은 줄도 <b>버리지 않고</b> 사유를 달아 남긴다 — 조용히 빠지면 사람은 전부
     * 들어갔다고 믿고, 빠진 줄은 몇 달 뒤 잔액이 안 맞을 때에야 드러난다.
     *
     * <p>규칙·카테고리·자산 이름은 <b>한 번만</b> 읽는다. 파일마다 다시 읽으면 아홉 장짜리
     * 이관이 같은 조회를 스물일곱 번 한다.
     *
     * <p>줄 수 상한은 <b>합계</b>로 센다. 파일마다 따로 세면 스무 장으로 상한의 스무 배가
     * 들어온다.
     */
    private List<List<ParsedRow>> parseAll(Long memberId, List<MultipartFile> files,
                                           List<? extends ImportDtos.FileRead> specs) {
        requirePaired(files, specs.size());
        List<LedgerAutoRule> rules = autoRuleService.rulesOf(memberId);
        Map<String, Long> categoryByName = categoryIdsByName(memberId);
        Map<String, Long> assetByName = assetIdsByName(memberId);

        List<List<ParsedRow>> byFile = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < files.size(); i++) {
            ImportDtos.FileRead spec = specs.get(i);
            if (spec.mapping().date() == null || !spec.mapping().hasAmountSource()) {
                throw new CustomException(ErrorCode.LEDGER_IMPORT_MAPPING_REQUIRED);
            }
            List<List<String>> rows = reader.read(files.get(i), spec.password());
            int skip = spec.skipRows() == null ? 1 : Math.max(spec.skipRows(), 0);

            List<ParsedRow> parsed = new ArrayList<>();
            for (int line = skip; line < rows.size(); line++) {
                parsed.add(parseRow(rows.get(line), line + 1, spec.mapping(), spec.dateFormat(),
                        rules, categoryByName, assetByName));
            }
            total += parsed.size();
            if (total > LedgerSheetReader.MAX_ROWS) {
                throw new CustomException(ErrorCode.LEDGER_IMPORT_TOO_MANY_ROWS);
            }
            byFile.add(parsed);
        }
        return byFile;
    }

    /**
     * 파일과 설정은 <b>순서로 짝</b>이다.
     *
     * <p>수가 어긋나면 짐작하지 않고 거부한다 — 짝이 밀린 채로 읽으면 은행 파일이 카드
     * 매핑으로 해석되고, 그 결과는 「오류」가 아니라 <b>그럴듯하게 틀린 줄</b>이다.
     */
    private void requirePaired(List<MultipartFile> files, int specCount) {
        if (files.size() > MAX_FILES) {
            throw new CustomException(ErrorCode.LEDGER_IMPORT_TOO_MANY_FILES);
        }
        if (files.size() != specCount) {
            throw new CustomException(ErrorCode.LEDGER_IMPORT_FILE_COUNT_MISMATCH);
        }
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

    /**
     * 앞 파일과 겹치는 줄 찾기(#1320).
     *
     * <p>견주는 규칙은 원장과 견줄 때와 <b>같다</b>(날짜 ±1일 · 금액 · 자산 · 내용). 다르면
     * 「원장에 있으면 걸리는데 앞 파일에 있으면 안 걸린다」는 설명할 수 없는 차이가 생긴다.
     *
     * <p>{@code prior}에는 <b>앞 파일의 줄만</b> 들어 있다 — 같은 파일 안의 줄끼리는 견주지
     * 않는다. 은행이 같은 날 같은 금액을 두 줄로 준 것은 실제로 두 번 일어난 일이다.
     */
    private ImportDtos.RowRef duplicateInPriorFiles(ParsedRow row, List<PriorRow> prior,
                                                    Long assetId) {
        String title = normalize(row.title);
        for (PriorRow other : prior) {
            if (!assetId.equals(other.assetId) || other.amount != row.amount) {
                continue;
            }
            long gap = Math.abs(other.occurredOn.toEpochDay() - row.occurredOn.toEpochDay());
            if (gap > DUPLICATE_WINDOW_DAYS) {
                continue;
            }
            if (title.isEmpty() || other.title.isEmpty() || title.equals(other.title)
                    || title.contains(other.title) || other.title.contains(title)) {
                return new ImportDtos.RowRef(other.fileIndex, other.rowNumber);
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

    /**
     * 앞 파일이 넣으려는 줄. 아직 원장에 없어서 id가 없으므로 <b>자리</b>로 가리킨다.
     *
     * @param title 이미 {@code normalize}를 지난 값. 줄마다 다시 다듬으면 파일 수의 제곱만큼 돈다
     */
    private record PriorRow(int fileIndex, int rowNumber, LocalDate occurredOn, long amount,
                            Long assetId, String title) {
    }
}
