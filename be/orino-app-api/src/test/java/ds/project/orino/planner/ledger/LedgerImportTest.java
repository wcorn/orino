package ds.project.orino.planner.ledger;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClock;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v2 이관(#1268) — 가져오기 · 되돌리기 · 자동 분류 · 포인트.
 *
 * <p>여기서 지키는 것은 「파일이 읽히나」가 아니라 <b>사람이 판단할 자리를 뺏지 않는가</b>다.
 * 중복은 보여줄 뿐 합치지 않고(`LDG-092`), 잘못 들어간 배치는 통째로 물릴 수 있으며
 * (`LDG-093`), 포인트는 자산 어디에도 섞이지 않는다(`LDG-006`).
 *
 * <p>시계를 못박는다(2026-01-15) — 미래 날짜는 예정으로 저장되므로 파일의 날짜가
 * 「오늘」의 어느 쪽인지에 따라 결과가 달라진다.
 */
@FixedClock
class LedgerImportTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
            handlerMapping;

    private String authHeader;
    private long checking;
    private long cafe;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
        cafe = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "카페/간식");
    }

    @Nested
    @DisplayName("가져오기")
    class Importing {

        @Test
        @DisplayName("CSV를 읽어 넣고, 넣은 줄만 원장에 남는다")
        void executesSelectedRows() throws Exception {
            String csv = """
                    날짜,내용,금액
                    2026-01-10,스타벅스 역삼,-5500
                    2026-01-11,편의점,-3200
                    """;

            // 실행 목록에 첫 줄만 담는다 — 사람이 둘째 줄 체크를 해제한 상황이다.
            execute(csv, "[2]")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.inserted").value(1))
                    .andExpect(jsonPath("$.data.skipped").value(1));

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("from", "2026-01-01")
                            .param("to", "2026-01-31"))
                    .andExpect(jsonPath("$.data.groups[*].items[*]", hasSize(1)));
        }

        /** 못 읽은 줄을 조용히 빼면 사람은 전부 들어갔다고 믿는다. */
        @Test
        @DisplayName("형식 오류를 버리지 않고 사유와 함께 보여준다")
        void keepsBrokenRows() throws Exception {
            String csv = """
                    날짜,내용,금액
                    2026-01-10,스타벅스,-5500
                    날짜아님,깨진 줄,-1000
                    """;

            preview(csv)
                    .andExpect(jsonPath("$.data.errorCount").value(1))
                    .andExpect(jsonPath("$.data.rows", hasSize(2)))
                    .andExpect(jsonPath("$.data.rows[1].error").value("날짜를 읽을 수 없습니다"))
                    // 화면에서 드러났다 — 못 읽은 줄이 「제목 없음 · 0원」으로 보였다.
                    // 파일에 적힌 말이 남아야 그 줄을 파일에서 찾을 수 있고,
                    // 금액은 0이 아니라 「모른다」다.
                    .andExpect(jsonPath("$.data.rows[1].title").value("깨진 줄"))
                    .andExpect(jsonPath("$.data.rows[1].amount").doesNotExist());
        }

        /**
         * 자동 병합 금지(`LDG-092`).
         *
         * <p>후보를 <b>보여주기만</b> 한다. 병합 엔드포인트가 없다는 것이 이 규칙의 실체다 —
         * 있으면 언젠가 「편의를 위해」 자동으로 부르게 된다.
         */
        @Test
        @DisplayName("중복 후보를 보여줄 뿐 합치지 않는다 — 병합 API가 없다")
        void showsDuplicatesWithoutMerging() throws Exception {
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 5500, "assetId": %d,
                     "occurredOn": "2026-01-10", "title": "스타벅스 역삼"}
                    """.formatted(checking));

            String csv = """
                    날짜,내용,금액
                    2026-01-10,스타벅스역삼,-5500
                    """;

            preview(csv)
                    .andExpect(jsonPath("$.data.duplicateCount").value(1))
                    // 어느 거래와 같아 보이는지까지 알려야 사람이 판단할 수 있다.
                    .andExpect(jsonPath("$.data.rows[0].duplicateOf").isNumber());

        }

        /**
         * <b>병합하는 문이 아예 없다</b>는 것이 `LDG-092`의 실체다.
         *
         * <p>상태 코드로 증명하지 않는다 — 없는 경로의 응답은 앱 설정에 달려 있어서, 그 설정이
         * 바뀌는 날 테스트가 엉뚱한 이유로 통과한다. 매핑 표를 직접 본다.
         */
        @Test
        @DisplayName("병합 엔드포인트가 등록되어 있지 않다")
        void hasNoMergeEndpoint() {
            boolean anyMerge = handlerMapping.getHandlerMethods().keySet().stream()
                    .flatMap(info -> info.getPathPatternsCondition() == null
                            ? java.util.stream.Stream.<String>empty()
                            : info.getPathPatternsCondition().getPatternValues().stream())
                    .anyMatch(path -> path.startsWith("/api/ledger/import")
                            && path.contains("merge"));

            org.assertj.core.api.Assertions.assertThat(anyMerge).isFalse();
        }

        /** 중복이라고 실행에서 막지 않는다 — 미리보기와 결과가 다르면 미리보기가 거짓말이 된다. */
        @Test
        @DisplayName("중복이어도 사람이 넣기로 했으면 넣는다")
        void insertsDuplicateWhenChosen() throws Exception {
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 5500, "assetId": %d,
                     "occurredOn": "2026-01-10", "title": "스타벅스 역삼"}
                    """.formatted(checking));

            execute("""
                    날짜,내용,금액
                    2026-01-10,스타벅스 역삼,-5500
                    """, "[2]")
                    .andExpect(jsonPath("$.data.inserted").value(1));
        }

        /** 은행 내역은 입금·출금이 두 열이다. 부호 하나로 오는 카드사와 다르다. */
        @Test
        @DisplayName("입금·출금이 두 열로 나뉜 파일도 읽는다")
        void readsTwoColumnAmounts() throws Exception {
            String csv = """
                    날짜,적요,출금,입금
                    2026-01-10,관리비,120000,
                    2026-01-11,급여,,3000000
                    """;
            String request = """
                    {"assetId": %d, "skipRows": 1,
                     "mapping": {"date": 0, "title": 1, "outflow": 2, "inflow": 3}}
                    """.formatted(checking);

            multipartPreview(csv, request)
                    .andExpect(jsonPath("$.data.rows[0].type").value("EXPENSE"))
                    .andExpect(jsonPath("$.data.rows[0].amount").value(120000))
                    .andExpect(jsonPath("$.data.rows[1].type").value("INCOME"))
                    .andExpect(jsonPath("$.data.rows[1].amount").value(3000000));
        }
    }

    /**
     * 은행 파일을 받은 그대로(#1318).
     *
     * <p>확인하는 것은 두 가지다 — <b>암호를 풀어 읽는가</b>, 그리고 <b>머리글이 1행이
     * 아니어도 찾는가</b>. 은행 거래내역은 비밀번호가 걸린 채로, 앞에 안내문을 달고 온다.
     */
    @Nested
    @DisplayName("은행 파일")
    class BankFiles {

        @Test
        @DisplayName("비밀번호를 함께 주면 암호 걸린 xlsx를 읽는다")
        void readsEncryptedWorkbook() throws Exception {
            byte[] encrypted = encryptedWorkbook("990820");

            mockMvc.perform(multipart("/api/ledger/import/analyze")
                            .file(xlsxPart(encrypted))
                            .param("password", "990820")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.headers[1]").value("거래일시"))
                    .andExpect(jsonPath("$.data.totalRows").value(2));
        }

        @Test
        @DisplayName("비밀번호가 없으면 「형식이 틀렸다」가 아니라 「암호가 걸렸다」고 답한다")
        void namesTheMissingPassword() throws Exception {
            mockMvc.perform(multipart("/api/ledger/import/analyze")
                            .file(xlsxPart(encryptedWorkbook("990820")))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-035"));
        }

        @Test
        @DisplayName("비밀번호가 틀리면 틀렸다고 답한다")
        void namesTheWrongPassword() throws Exception {
            mockMvc.perform(multipart("/api/ledger/import/analyze")
                            .file(xlsxPart(encryptedWorkbook("990820")))
                            .param("password", "000000")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-036"));
        }

        /**
         * 카카오뱅크는 앞 10줄이 제목·성명·계좌번호·주의사항이고 11행이 머리글이다.
         * 1행을 머리글로 못 박으면 화면의 열 이름이 전부 「(이름 없음)」이 된다.
         */
        @Test
        @DisplayName("안내문이 앞에 붙어 있어도 머리글 줄을 찾아낸다")
        void findsHeaderBelowPreamble() throws Exception {
            String csv = """
                    카카오뱅크 거래내역,,,
                    ,,,
                    성명,강동석,조회기간,2025.09.01 - 2026.09.01
                    계좌번호,****-**-***9981,요청일시,2026.09.01 16:12:21
                    ,,,
                    ※ 금액앞에 '-' 표시는 출금 금액입니다.,,,
                    거래일시,구분,거래금액,내용
                    2026.01.10 09:12:00,출금,"-5,500",스타벅스 역삼
                    2026.01.11 10:00:00,입금,"30,000",박순요
                    2026.01.12 11:00:00,출금,"-3,200",편의점
                    """;

            mockMvc.perform(multipart("/api/ledger/import/analyze")
                            .file(csvPart(csv))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    // 0부터 센다 — 화면의 「건너뛸 머리글 줄 수」는 이 값 + 1이다.
                    .andExpect(jsonPath("$.data.headerRow").value(6))
                    .andExpect(jsonPath("$.data.headers[0]").value("거래일시"))
                    .andExpect(jsonPath("$.data.headers[2]").value("거래금액"))
                    .andExpect(jsonPath("$.data.totalRows").value(3))
                    // 표본도 머리글 다음부터다. 안내문을 보여 주면 확인할 근거가 못 된다.
                    .andExpect(jsonPath("$.data.sample[0][3]").value("스타벅스 역삼"));
        }

        @Test
        @DisplayName("머리글이 1행인 파일은 그대로 1행이다 — 새 규칙이 옛 파일을 깨지 않는다")
        void keepsFirstRowWhenItIsTheHeader() throws Exception {
            String csv = """
                    날짜,내용,금액
                    2026-01-10,스타벅스 역삼,-5500
                    2026-01-11,편의점,-3200
                    """;

            mockMvc.perform(multipart("/api/ledger/import/analyze")
                            .file(csvPart(csv))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.headerRow").value(0))
                    .andExpect(jsonPath("$.data.totalRows").value(2));
        }

        /** 카카오뱅크 파일과 같은 모양 — 안내문 6줄 + 머리글 + 「-1,234」 꼴의 금액. */
        private byte[] encryptedWorkbook(String password) throws Exception {
            byte[] plain;
            try (XSSFWorkbook workbook = new XSSFWorkbook();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("카카오뱅크 거래내역");
                sheet.createRow(0).createCell(1).setCellValue("카카오뱅크 거래내역");
                Row header = sheet.createRow(1);
                header.createCell(1).setCellValue("거래일시");
                header.createCell(2).setCellValue("구분");
                header.createCell(3).setCellValue("거래금액");
                for (int i = 0; i < 2; i++) {
                    Row row = sheet.createRow(2 + i);
                    row.createCell(1).setCellValue("2026.01.1" + i + " 09:12:00");
                    row.createCell(2).setCellValue("출금");
                    row.createCell(3).setCellValue("-5,50" + i);
                }
                workbook.write(out);
                plain = out.toByteArray();
            }

            try (POIFSFileSystem fs = new POIFSFileSystem();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile);
                Encryptor encryptor = info.getEncryptor();
                encryptor.confirmPassword(password);
                try (OutputStream encrypted = encryptor.getDataStream(fs)) {
                    encrypted.write(plain);
                }
                fs.writeFilesystem(out);
                return out.toByteArray();
            }
        }

        private MockMultipartFile xlsxPart(byte[] bytes) {
            return new MockMultipartFile("file", "거래내역.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        }
    }

    @Nested
    @DisplayName("배치 되돌리기")
    class Reverting {

        /** 잘못 들어가면 잔액·통계·청구서가 전부 틀어진다. 통째로 물릴 수 있어야 한다. */
        @Test
        @DisplayName("되돌리면 그 배치로 들어온 행만 사라진다")
        void revertsOnlyImportedRows() throws Exception {
            // 손으로 적은 줄. 되돌리기에 휩쓸리면 그건 복구가 아니라 사고다.
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 9900, "assetId": %d,
                     "occurredOn": "2026-01-09", "title": "손으로 적은 줄"}
                    """.formatted(checking));

            String body = execute("""
                    날짜,내용,금액
                    2026-01-10,스타벅스,-5500
                    2026-01-11,편의점,-3200
                    """, "[2,3]")
                    .andExpect(jsonPath("$.data.inserted").value(2))
                    .andReturn().getResponse().getContentAsString();
            long batchId = batchIdOf(body);

            mockMvc.perform(post("/api/ledger/import/batches/" + batchId + "/revert")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.reverted").value(2));

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("from", "2026-01-01")
                            .param("to", "2026-01-31"))
                    .andExpect(jsonPath("$.data.groups[*].items[*]", hasSize(1)))
                    .andExpect(jsonPath("$.data.groups[0].items[0].title")
                            .value("손으로 적은 줄"));
        }

        /** 두 번째는 아무 일도 안 하는데 화면은 성공으로 읽는다. 그 말을 갈라 놓는다. */
        @Test
        @DisplayName("두 번 되돌릴 수 없다")
        void rejectsDoubleRevert() throws Exception {
            String body = execute("""
                    날짜,내용,금액
                    2026-01-10,스타벅스,-5500
                    """, "[2]").andReturn().getResponse().getContentAsString();
            long batchId = batchIdOf(body);

            mockMvc.perform(post("/api/ledger/import/batches/" + batchId + "/revert")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/ledger/import/batches/" + batchId + "/revert")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-028"));
        }

        /** 되돌린 배치도 목록에 남는다 — 「무엇을 넣었다가 물렀는지」도 이력이다. */
        @Test
        @DisplayName("되돌린 배치가 이력에서 사라지지 않는다")
        void keepsRevertedBatchInHistory() throws Exception {
            String body = execute("""
                    날짜,내용,금액
                    2026-01-10,스타벅스,-5500
                    """, "[2]").andReturn().getResponse().getContentAsString();
            long batchId = batchIdOf(body);
            mockMvc.perform(post("/api/ledger/import/batches/" + batchId + "/revert")
                    .header(HttpHeaders.AUTHORIZATION, authHeader));

            mockMvc.perform(get("/api/ledger/import/batches")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].revertedAt").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("자동 분류")
    class AutoRules {

        @Test
        @DisplayName("가져오기가 규칙을 지나간다")
        void appliesToImport() throws Exception {
            createRule("스타벅스", "CONTAINS", cafe);

            preview("""
                    날짜,내용,금액
                    2026-01-10,스타벅스 역삼,-5500
                    """)
                    .andExpect(jsonPath("$.data.rows[0].categoryId").value((int) cafe))
                    .andExpect(jsonPath("$.data.rows[0].categoryName").value("카페/간식"));
        }

        /**
         * <b>수동 입력에도 적용된다.</b> 가져오기 쪽에만 두면 손으로 적은 거래는 분류되지
         * 않고, 그러면 같은 규칙이 두 곳에 살게 된다.
         */
        @Test
        @DisplayName("손으로 적어도 규칙이 걸린다")
        void appliesToManualInput() throws Exception {
            createRule("스타벅스", "CONTAINS", cafe);

            manualExpense("""
                    {"type": "EXPENSE", "amount": 5500, "assetId": %d,
                     "occurredOn": "2026-01-10", "title": "스타벅스 강남"}
                    """.formatted(checking))
                    .andExpect(jsonPath("$.data.transaction.categoryId").value((int) cafe));
        }

        /** 덮어쓰면 「분명 바꿨는데 되돌아간다」가 되고, 그때 사람은 자동 분류를 꺼 버린다. */
        @Test
        @DisplayName("사람이 고른 카테고리를 덮지 않는다")
        void doesNotOverrideChosenCategory() throws Exception {
            long food = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");
            createRule("스타벅스", "CONTAINS", cafe);

            manualExpense("""
                    {"type": "EXPENSE", "amount": 5500, "assetId": %d, "categoryId": %d,
                     "occurredOn": "2026-01-10", "title": "스타벅스 강남"}
                    """.formatted(checking, food))
                    .andExpect(jsonPath("$.data.transaction.categoryId").value((int) food));
        }

        /** 수입·이체에 지출 카테고리가 붙으면 통계에서 같은 돈이 두 번 세어진다. */
        @Test
        @DisplayName("지출 카테고리만 규칙으로 걸 수 있다")
        void rejectsIncomeCategory() throws Exception {
            long salary = LedgerFixture.categoryIdByName(mockMvc, authHeader, "INCOME", "급여");

            mockMvc.perform(post("/api/ledger/auto-rules")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"keyword": "월급", "matchType": "CONTAINS", "categoryId": %d}
                                    """.formatted(salary)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-032"));
        }
    }

    @Nested
    @DisplayName("포인트")
    class Points {

        /**
         * <b>포인트는 돈이 아니다</b>(`LDG-006`). 총자산·순자산 어디에도 들어가지 않는다 —
         * 섞이는 순간 「자산이 얼마인가」가 답할 수 없는 질문이 된다.
         */
        @Test
        @DisplayName("총자산·순자산에 잡히지 않는다")
        void neverCountsAsAsset() throws Exception {
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "INCOME", "amount": 1000000, "assetId": %d,
                     "occurredOn": "2026-01-05", "title": "급여"}
                    """.formatted(checking));

            mockMvc.perform(post("/api/ledger/points")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "네이버페이", "unit": "포인트", "balance": 50000}
                                    """))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.totalAssets").value(1000000))
                    .andExpect(jsonPath("$.data.netWorth").value(1000000));
        }

        @Test
        @DisplayName("소멸 D-day를 서버가 계산해 내린다")
        void countsDaysToExpiry() throws Exception {
            mockMvc.perform(post("/api/ledger/points")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name": "OK캐쉬백", "unit": "포인트", "balance": 3000,
                             "expiresOn": "2026-01-25"}
                            """));

            mockMvc.perform(get("/api/ledger/points")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data[0].daysLeft").value(10))
                    .andExpect(jsonPath("$.data[0].expiringSoon").value(true));
        }
    }

    @Nested
    @DisplayName("내보내기")
    class Exporting {

        /** 내보내기는 백업이다. 다시 넣을 수 없으면 백업이 아니라 보기 좋은 표다. */
        @Test
        @DisplayName("내보낸 CSV를 그대로 다시 가져올 수 있다")
        void roundTrips() throws Exception {
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 5500, "assetId": %d,
                     "occurredOn": "2026-01-10", "title": "스타벅스 역삼"}
                    """.formatted(checking));

            byte[] exported = mockMvc.perform(get("/api/ledger/export")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("from", "2026-01-01")
                            .param("to", "2026-01-31"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();

            // 내보낸 그대로 되넣는다 — 날짜 0열 · 유형 1열 · 금액 2열 · 내용 5열.
            String request = """
                    {"assetId": %d, "skipRows": 1,
                     "mapping": {"date": 0, "type": 1, "amount": 2, "title": 5, "memo": 6}}
                    """.formatted(checking);
            mockMvc.perform(multipart("/api/ledger/import/preview")
                            .file(new MockMultipartFile("file", "export.csv", "text/csv", exported))
                            .file(jsonPart(request))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.errorCount").value(0))
                    .andExpect(jsonPath("$.data.rows[0].amount").value(5500))
                    .andExpect(jsonPath("$.data.rows[0].type").value("EXPENSE"))
                    .andExpect(jsonPath("$.data.rows[0].title").value("스타벅스 역삼"))
                    // 같은 거래가 이미 원장에 있으니 중복 후보로 잡혀야 한다.
                    .andExpect(jsonPath("$.data.duplicateCount").value(1));
        }

        /**
         * 로컬에서 드러났다 — 내보낸 파일엔 자산 열이 있는데 가져오기가 읽지 않아,
         * <b>백업을 되돌리면 여러 계좌가 한 자산으로 뭉쳤다.</b> 카드사 파일은 계좌가 하나라
         * 문제가 안 되지만, 백업은 정의상 여러 자산이 한 파일에 섞여 있다.
         */
        @Test
        @DisplayName("백업을 되돌리면 자산이 뭉치지 않는다")
        void restoresEachRowToItsOwnAsset() throws Exception {
            long card = LedgerFixture.createAsset(mockMvc, authHeader, "신한카드", "CREDIT_CARD");
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 5500, "assetId": %d,
                     "occurredOn": "2026-01-10", "title": "통장에서"}
                    """.formatted(checking));
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 7700, "assetId": %d,
                     "occurredOn": "2026-01-11", "title": "카드로"}
                    """.formatted(card));

            byte[] exported = mockMvc.perform(get("/api/ledger/export")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("from", "2026-01-01")
                            .param("to", "2026-01-31"))
                    .andReturn().getResponse().getContentAsByteArray();

            // 자산 열(3)을 매핑에 넣는다. 기본 자산은 통장이지만 카드 줄은 카드로 가야 한다.
            String request = """
                    {"assetId": %d, "skipRows": 1,
                     "mapping": {"date": 0, "type": 1, "amount": 2, "asset": 3,
                                 "title": 5, "memo": 6}}
                    """.formatted(checking);
            mockMvc.perform(multipart("/api/ledger/import/preview")
                            .file(new MockMultipartFile("file", "export.csv", "text/csv", exported))
                            .file(jsonPart(request))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.rows[0].assetName").value("신한카드"))
                    .andExpect(jsonPath("$.data.rows[1].assetName").value("급여통장"))
                    // 제 자산으로 견줘야 둘 다 중복으로 잡힌다.
                    .andExpect(jsonPath("$.data.duplicateCount").value(2));
        }

        /** 이름이 하나 안 맞는다고 복원이 통째로 멈추면 안 된다. */
        @Test
        @DisplayName("모르는 자산 이름은 기본 자산이 받는다")
        void fallsBackToChosenAsset() throws Exception {
            String csv = """
                    날짜,유형,금액,자산,카테고리,내용,메모
                    2026-01-10,지출,5500,없어진 계좌,,스타벅스,
                    """;
            String request = """
                    {"assetId": %d, "skipRows": 1,
                     "mapping": {"date": 0, "type": 1, "amount": 2, "asset": 3,
                                 "title": 5, "memo": 6}}
                    """.formatted(checking);

            multipartPreview(csv, request)
                    .andExpect(jsonPath("$.data.errorCount").value(0))
                    .andExpect(jsonPath("$.data.rows[0].assetName").value("급여통장"));
        }
    }

    // --- 도우미 ---

    /** 손으로 적는 경로. 규칙이 여기도 걸리는지 보려면 응답을 그대로 봐야 한다. */
    private ResultActions manualExpense(String json) throws Exception {
        return mockMvc.perform(post("/api/ledger/transactions")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void createRule(String keyword, String matchType, long categoryId) throws Exception {
        mockMvc.perform(post("/api/ledger/auto-rules")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyword": "%s", "matchType": "%s", "categoryId": %d}
                                """.formatted(keyword, matchType, categoryId)))
                .andExpect(status().isOk());
    }

    private ResultActions preview(String csv) throws Exception {
        return multipartPreview(csv, """
                {"assetId": %d, "skipRows": 1,
                 "mapping": {"date": 0, "title": 1, "amount": 2}}
                """.formatted(checking));
    }

    private ResultActions multipartPreview(String csv, String request) throws Exception {
        return mockMvc.perform(multipart("/api/ledger/import/preview")
                        .file(csvPart(csv))
                        .file(jsonPart(request))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    private ResultActions execute(String csv, String rowNumbers) throws Exception {
        String request = """
                {"assetId": %d, "skipRows": 1, "source": "테스트",
                 "mapping": {"date": 0, "title": 1, "amount": 2},
                 "rowNumbers": %s}
                """.formatted(checking, rowNumbers);
        return mockMvc.perform(multipart("/api/ledger/import/execute")
                .file(csvPart(csv))
                .file(jsonPart(request))
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    private MockMultipartFile csvPart(String csv) {
        return new MockMultipartFile("file", "sample.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile jsonPart(String json) {
        return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private long batchIdOf(String body) {
        int at = body.indexOf("\"batchId\":");
        String rest = body.substring(at + "\"batchId\":".length());
        return Long.parseLong(rest.split("[,}]")[0].trim());
    }
}
