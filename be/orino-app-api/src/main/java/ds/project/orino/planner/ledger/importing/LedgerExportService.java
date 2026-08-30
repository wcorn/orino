package ds.project.orino.planner.ledger.importing;

import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerCategoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 내보내기(`LDG-094`).
 *
 * <p><b>재가져오기 가능한 형식으로 유지한다.</b> 내보내기는 이관 기능이자 <b>백업</b>이라,
 * 가져오기와 별개로 성립해야 한다 — 내보낸 파일을 다시 넣을 수 없다면 그건 백업이 아니라
 * 그냥 보기 좋은 표다.
 *
 * <p>그래서 열 이름과 값 형식을 {@link LedgerImportService}가 읽는 그대로 쓴다.
 * 날짜는 ISO, 금액은 <b>부호 없는 수</b>에 유형 열이 방향을 말한다 — 원장의 규칙과 같다.
 */
@Service
public class LedgerExportService {

    /** 열 순서. <b>바꾸지 않는다</b> — 예전에 내보낸 파일도 같은 매핑으로 다시 들어와야 한다. */
    private static final List<String> HEADERS =
            List.of("날짜", "유형", "금액", "자산", "카테고리", "내용", "메모");

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerAssetRepository assetRepository;
    private final LedgerCategoryRepository categoryRepository;

    public LedgerExportService(LedgerTransactionRepository transactionRepository,
                               LedgerAssetRepository assetRepository,
                               LedgerCategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public byte[] toCsv(Long memberId, LocalDate from, LocalDate to) {
        StringBuilder csv = new StringBuilder();
        // 엑셀이 UTF-8로 열도록 BOM을 붙인다. 없으면 한글 열 이름이 깨져 보이고,
        // 깨진 파일을 사람은 「내보내기가 고장났다」로 읽는다.
        csv.append('﻿');
        csv.append(String.join(",", HEADERS)).append('\n');
        for (List<String> row : rows(memberId, from, to)) {
            csv.append(row.stream().map(LedgerExportService::quote).toList()
                    .stream().reduce((a, b) -> a + "," + b).orElse(""));
            csv.append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] toXlsx(Long memberId, LocalDate from, LocalDate to) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("거래");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                header.createCell(i).setCellValue(HEADERS.get(i));
            }

            int index = 1;
            for (List<String> row : rows(memberId, from, to)) {
                Row sheetRow = sheet.createRow(index++);
                for (int i = 0; i < row.size(); i++) {
                    // 전부 문자열로 쓴다. 날짜를 날짜 셀로 두면 엑셀이 지역 서식으로 보여 주고,
                    // 그 파일을 다시 넣을 때 표기가 달라져 있다.
                    sheetRow.createCell(i).setCellValue(row.get(i));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 내보낼 줄.
     *
     * <p><b>예정 거래는 뺀다.</b> 아직 일어나지 않은 일이라 백업의 대상이 아니고, 다시
     * 넣으면 「그때 그렇게 될 예정이었다」가 확정 거래로 둔갑한다.
     */
    private List<List<String>> rows(Long memberId, LocalDate from, LocalDate to) {
        Map<Long, String> assets = new HashMap<>();
        assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId)
                .forEach(asset -> assets.put(asset.getId(), asset.getName()));
        Map<Long, String> categories = new HashMap<>();
        categoryRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId)
                .forEach(category -> categories.put(category.getId(), category.getName()));

        return transactionRepository
                .findAllByMemberIdAndDeletedAtIsNullAndOccurredOnBetweenOrderByOccurredOnDescIdDesc(
                        memberId, from, to).stream()
                .filter(tx -> tx.getStatus() == LedgerTransactionStatus.CONFIRMED)
                .map(tx -> row(tx, assets, categories))
                .toList();
    }

    private List<String> row(LedgerTransaction tx, Map<Long, String> assets,
                             Map<Long, String> categories) {
        return List.of(
                tx.getOccurredOn().toString(),
                switch (tx.getType()) {
                    case EXPENSE -> "지출";
                    case INCOME -> "수입";
                    case TRANSFER -> "이체";
                },
                String.valueOf(tx.getAmount()),
                nullToEmpty(assets.get(tx.getAssetId())),
                nullToEmpty(categories.get(tx.getCategoryId())),
                nullToEmpty(tx.getTitle()),
                nullToEmpty(tx.getMemo()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 쉼표·따옴표·줄바꿈이 든 값만 감싼다 — 전부 감싸면 사람이 열었을 때 읽기 나쁘다. */
    private static String quote(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
