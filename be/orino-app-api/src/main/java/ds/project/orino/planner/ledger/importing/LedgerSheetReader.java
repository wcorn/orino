package ds.project.orino.planner.ledger.importing;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.hssf.record.crypto.Biff8EncryptionKey;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 파일을 <b>문자열 격자</b>로만 읽는다.
 *
 * <p>여기서는 뜻을 해석하지 않는다 — 어느 열이 날짜이고 어느 열이 금액인지는 매핑이 정하고,
 * 그건 다음 단계의 일이다. 읽기와 해석을 섞으면 「이 파일은 왜 안 되나」를 물었을 때
 * 파일 문제인지 매핑 문제인지 갈라 볼 수 없다.
 *
 * <p>엑셀은 <b>.xlsx와 구형 .xls</b>를 받는다(D-6에서 BE POI로 정했다). 국민은행은 거래내역을
 * .xls로 내려준다(#1381). .xls를 읽는 HSSF는 이미 쓰는 {@code poi} jar에 들어 있어
 * 의존성이 늘지 않는다.
 *
 * <p><b>암호가 걸린 xlsx는 비밀번호를 받아 푼다</b>(#1318). 은행 거래내역은 비밀번호를 걸어
 * 내려주는 것이 기본이라, 받지 않으면 사람이 엑셀로 열어 다시 저장하는 단계를 매번 거쳐야 한다.
 * 비밀번호는 <b>그 요청에서만 쓰고 어디에도 남기지 않는다</b> — 저장하면 지킬 것이 하나 는다.
 */
@Component
public class LedgerSheetReader {

    /**
     * 한 번에 읽을 줄의 상한.
     *
     * <p>이관은 「몇 년치 한 번」이라 넉넉해야 하지만, 상한이 없으면 잘못 고른 파일 하나가
     * 힙을 통째로 먹는다. 넘으면 <b>거절하고 말한다</b> — 조용히 앞부분만 넣으면 사람은
     * 전부 들어갔다고 믿는다.
     */
    static final int MAX_ROWS = 20_000;

    /** 셀 값을 화면에 보이는 대로 읽는다 — 날짜 서식이 걸린 셀이 42736 같은 수로 나오지 않게. */
    private final DataFormatter formatter = new DataFormatter();

    public List<List<String>> read(MultipartFile file) {
        return read(file, null);
    }

    /** @param password 암호가 걸린 엑셀 파일의 비밀번호. 없으면 {@code null} */
    public List<List<String>> read(MultipartFile file, String password) {
        String name = file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase();
        List<List<String>> rows;
        if (name.endsWith(".xlsx")) {
            rows = readExcel(file, password, false);
        } else if (name.endsWith(".xls")) {
            rows = readExcel(file, password, true);
        } else if (name.endsWith(".csv") || name.endsWith(".txt")) {
            rows = readCsv(file);
        } else {
            throw new CustomException(ErrorCode.LEDGER_IMPORT_UNSUPPORTED_FILE);
        }

        // 아래쪽 빈 줄은 엑셀에서 흔하다. 셀을 지운 자리가 「행」으로는 남아 있어서다.
        while (!rows.isEmpty() && isBlank(rows.get(rows.size() - 1))) {
            rows.remove(rows.size() - 1);
        }
        if (rows.isEmpty()) {
            throw new CustomException(ErrorCode.LEDGER_IMPORT_EMPTY_FILE);
        }
        if (rows.size() > MAX_ROWS) {
            throw new CustomException(ErrorCode.LEDGER_IMPORT_TOO_MANY_ROWS);
        }
        return rows;
    }

    /** @param legacy 구형 .xls(BIFF8)인가 */
    private List<List<String>> readExcel(MultipartFile file, String password, boolean legacy) {
        try (InputStream in = file.getInputStream();
             Workbook workbook = legacy ? openXls(in, password) : openXlsx(in, password)) {
            // 첫 시트만 읽는다. 여러 시트를 합치면 어느 시트에서 온 줄인지 알 수 없다.
            Sheet sheet = workbook.getSheetAt(0);
            List<List<String>> rows = new ArrayList<>();
            // 번호로 돈다. 시트의 반복자는 통째로 빈 행을 건너뛰어, 국민은행 파일처럼 안내문
            // 아래 빈 줄이 있으면 뒤 줄이 한 칸씩 당겨진다 — 엑셀의 5행이 여기서 4번째가 되어
            // 사람이 엑셀을 보고 적은 「건너뛸 줄 수」가 한 줄 밀린다. 빈 셀과 같은 이유다.
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                rows.add(row == null ? new ArrayList<>() : cellsOf(row));
                if (rows.size() > MAX_ROWS) {
                    throw new CustomException(ErrorCode.LEDGER_IMPORT_TOO_MANY_ROWS);
                }
            }
            return rows;
        } catch (IOException | RuntimeException e) {
            if (e instanceof CustomException custom) {
                throw custom;
            }
            throw new CustomException(ErrorCode.LEDGER_IMPORT_UNSUPPORTED_FILE);
        }
    }

    private Workbook openXlsx(InputStream raw, String password) throws IOException {
        // 매직 넘버를 보려면 되감을 수 있어야 한다. 확장자는 .xlsx인데 알맹이가
        // 암호화 컨테이너(OLE2)인 것이 은행 파일의 기본 모습이다.
        InputStream in = FileMagic.prepareToCheckMagic(raw);
        return FileMagic.valueOf(in) == FileMagic.OLE2
                ? decrypt(in, password)
                : new XSSFWorkbook(in);
    }

    /**
     * 구형 .xls(BIFF8)를 연다.
     *
     * <p>.xls의 암호는 컨테이너가 아니라 레코드마다 걸려 있어, POI는 비밀번호를
     * <b>스레드에 붙여 두고</b> 여는 동안 읽는다. 열고 나면 반드시 비운다 — 남겨 두면 같은
     * 스레드가 다음 요청에서 앞사람의 비밀번호로 파일을 연다.
     *
     * <p>레코드는 생성자에서 모두 읽히므로, 비운 뒤에 셀을 읽어도 된다.
     */
    private Workbook openXls(InputStream in, String password) throws IOException {
        boolean given = password != null && !password.isBlank();
        Biff8EncryptionKey.setCurrentUserPassword(given ? password : null);
        try {
            return new HSSFWorkbook(in);
        } catch (EncryptedDocumentException e) {
            throw new CustomException(given
                    ? ErrorCode.LEDGER_IMPORT_PASSWORD_WRONG
                    : ErrorCode.LEDGER_IMPORT_PASSWORD_REQUIRED);
        } finally {
            Biff8EncryptionKey.setCurrentUserPassword(null);
        }
    }

    /**
     * 암호를 풀어 연다.
     *
     * <p>비밀번호가 없으면 <b>없다고 말한다</b>. 「형식이 틀렸다」로 답하면 사람은 .xlsx를
     * .xlsx로 바꾸려 들고, 무엇이 잘못됐는지 끝내 알지 못한다.
     */
    private Workbook decrypt(InputStream in, String password) throws IOException {
        if (password == null || password.isBlank()) {
            throw new CustomException(ErrorCode.LEDGER_IMPORT_PASSWORD_REQUIRED);
        }
        POIFSFileSystem fs = new POIFSFileSystem(in);
        try {
            Decryptor decryptor = Decryptor.getInstance(new EncryptionInfo(fs));
            if (!decryptor.verifyPassword(password)) {
                throw new CustomException(ErrorCode.LEDGER_IMPORT_PASSWORD_WRONG);
            }
            return new XSSFWorkbook(decryptor.getDataStream(fs));
        } catch (GeneralSecurityException e) {
            throw new CustomException(ErrorCode.LEDGER_IMPORT_PASSWORD_WRONG);
        }
    }

    private List<String> cellsOf(Row row) {
        List<String> cells = new ArrayList<>();
        // getLastCellNum은 「마지막 셀 다음」이라 그대로 상한으로 쓴다. 빈 셀도 자리를
        // 지켜야 열 번호로 매핑할 수 있다 — 건너뛰면 뒤 열이 앞으로 밀린다.
        for (int i = 0; i < Math.max(row.getLastCellNum(), 0); i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            cells.add(cell == null ? "" : valueOf(cell));
        }
        return cells;
    }

    /**
     * 셀 하나를 문자열로.
     *
     * <p>날짜 셀은 <b>ISO로 적어 내린다.</b> 엑셀의 표시 서식은 파일마다 달라
     * ({@code 2026. 8. 25}, {@code 08/25/26}) 그대로 두면 파싱 규칙이 파일 수만큼 는다.
     */
    private String valueOf(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC
                && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime at = cell.getLocalDateTimeCellValue();
            return at == null ? "" : at.toLocalDate().toString();
        }
        return formatter.formatCellValue(cell).trim();
    }

    /**
     * CSV.
     *
     * <p>따옴표 안의 쉼표와 두 겹 따옴표({@code ""})만 다룬다 — 은행·카드사 내보내기가
     * 실제로 쓰는 범위다. 그 밖의 방언을 흉내 내려 들면 라이브러리를 하나 더 들여야 하고,
     * 그건 이 기능이 감당할 무게가 아니다.
     */
    private List<List<String>> readCsv(MultipartFile file) {
        try (InputStream in = file.getInputStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<List<String>> rows = new ArrayList<>();
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    // 엑셀이 UTF-8 CSV 앞에 붙이는 BOM. 남겨 두면 첫 열 이름이 안 맞는다.
                    line = line.replace("﻿", "");
                    first = false;
                }
                rows.add(splitCsv(line));
                if (rows.size() > MAX_ROWS) {
                    throw new CustomException(ErrorCode.LEDGER_IMPORT_TOO_MANY_ROWS);
                }
            }
            return rows;
        } catch (IOException e) {
            throw new CustomException(ErrorCode.LEDGER_IMPORT_UNSUPPORTED_FILE);
        }
    }

    private List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().trim());
        return cells;
    }

    private boolean isBlank(List<String> row) {
        return row.stream().allMatch(cell -> cell == null || cell.isBlank());
    }

    /** 날짜 한 칸. 형식을 못 읽으면 {@code null}이고, 그 줄은 「형식 오류」로 보여 준다. */
    static LocalDate parseDate(String raw, String pattern) {
        return LedgerDateParser.parse(raw, pattern);
    }
}
