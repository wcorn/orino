package ds.project.orino.planner.dataset.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * .xlsx 가져오기(#1310 · Epic #892 c).
 *
 * <p>이 슬라이스의 완료 판정은 한 문장이다 — <b>내보낸 파일을 도로 가져오면 같은 표가 나온다.</b>
 * 값·수식·서식·병합·요약 어느 하나가 새면 그 자리에서 드러난다.
 */
class DatasetImportTest extends ApiTestSupport {

    private static final String XLSX_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    private long createDataset(String columnsJson) throws Exception {
        String body = mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"columns\":" + columnsJson + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private void bulk(long datasetId, String rowsJson) throws Exception {
        mockMvc.perform(post("/api/datasets/{id}/rows/bulk", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":" + rowsJson + "}"))
                .andExpect(status().isOk());
    }

    private void patchRow(long datasetId, int rowIndex, String cells) throws Exception {
        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", datasetId, rowIndex)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":" + cells + "}"))
                .andExpect(status().isOk());
    }

    private byte[] export(long datasetId) throws Exception {
        return mockMvc.perform(get("/api/datasets/{id}/export", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private ResultActions importFile(byte[] bytes, String query) throws Exception {
        return mockMvc.perform(multipart("/api/datasets/import" + query)
                .file(new MockMultipartFile("file", "표.xlsx", XLSX_TYPE, bytes))
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    private long importAndGetId(byte[] bytes) throws Exception {
        String body = importFile(bytes, "")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.datasetId")).longValue();
    }

    private String meta(long datasetId) throws Exception {
        return mockMvc.perform(get("/api/datasets/{id}", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String rows(long datasetId) throws Exception {
        return mockMvc.perform(get("/api/datasets/{id}/rows", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** 한 시트짜리 최소 통합문서. 남의 파일을 흉내 낼 때 쓴다. */
    private byte[] workbook(String sheetName, String[][] cells) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            for (int r = 0; r < cells.length; r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < cells[r].length; c++) {
                    String value = cells[r][c];
                    if (value == null) {
                        continue;
                    }
                    if (value.startsWith("=")) {
                        row.createCell(c).setCellFormula(value.substring(1));
                    } else {
                        row.createCell(c).setCellValue(value);
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Nested
    @DisplayName("왕복 — 내보낸 파일을 도로 가져오면 같은 표다")
    class RoundTrip {

        private long source() throws Exception {
            long id = createDataset("""
                    [{"key":"c0","label":"품목"},{"key":"c1","label":"단가"},
                     {"key":"c2","label":"수량"},{"key":"c3","label":"금액"}]
                    """);
            bulk(id, """
                    [["연필","500","3",""],["공책","1200","2",""]]
                    """);
            patchRow(id, 0, "[\"연필\",\"500\",\"3\",\"={단가} * {수량}\"]");
            patchRow(id, 1, "[\"공책\",\"1200\",\"2\",\"={단가} * {수량}\"]");
            return id;
        }

        @Test
        @DisplayName("표 이름도 돌아온다 — 시트 이름으로 실려 갔다가 되돌아온다")
        void tableNameSurvives() throws Exception {
            long id = source();
            mockMvc.perform(patch("/api/datasets/{id}/name", id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"주문 내역\"}"))
                    .andExpect(status().isOk());

            long imported = importAndGetId(export(id));

            assertThat(JsonPath.<String>read(meta(imported), "$.data.name"))
                    .isEqualTo("주문 내역");
        }

        @Test
        @DisplayName("값과 열 이름이 그대로 온다")
        void valuesAndHeaders() throws Exception {
            long imported = importAndGetId(export(source()));

            String metaJson = meta(imported);
            assertThat(JsonPath.<String>read(metaJson, "$.data.columns[0].label")).isEqualTo("품목");
            assertThat(JsonPath.<String>read(metaJson, "$.data.columns[3].label")).isEqualTo("금액");
            assertThat(JsonPath.<Integer>read(metaJson, "$.data.rowCount")).isEqualTo(2);

            String rowsJson = rows(imported);
            assertThat(JsonPath.<String>read(rowsJson, "$.data.rows[0].cells[0]")).isEqualTo("연필");
            assertThat(JsonPath.<String>read(rowsJson, "$.data.rows[1].cells[0]")).isEqualTo("공책");
        }

        @Test
        @DisplayName("수식은 값이 아니라 수식으로 살아 온다 — 원본 표와 글자까지 같다")
        void formulasSurvive() throws Exception {
            long id = source();
            String before = rows(id);
            long imported = importAndGetId(export(id));
            String after = rows(imported);

            // 계산된 값이 맞고,
            assertThat(JsonPath.<String>read(after, "$.data.rows[0].cells[3]")).isEqualTo("1500");
            assertThat(JsonPath.<String>read(after, "$.data.rows[1].cells[3]")).isEqualTo("2400");
            // 값이 아니라 수식으로 들어 있다 — 원본이 보여주던 그 수식 그대로.
            // (표시형은 우리 writer가 트리에서 만들므로 괄호까지 원본과 같아야 한다.)
            for (int r = 0; r < 2; r++) {
                assertThat(JsonPath.<String>read(after, "$.data.rows[%d].formulas.c3".formatted(r)))
                        .isEqualTo(JsonPath.read(before, "$.data.rows[%d].formulas.c3".formatted(r)));
            }
        }

        @Test
        @DisplayName("들어온 수식이 살아 있다 — 단가를 고치면 금액이 따라 바뀐다")
        void importedFormulasRecalculate() throws Exception {
            long imported = importAndGetId(export(source()));

            mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", imported, 0)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"cells\":[\"연필\",\"1000\",\"3\",\"={단가} * {수량}\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.edited.cells[3]").value("3000"));
        }

        @Test
        @DisplayName("특정 행을 가리키던 수식은 그 행을 계속 가리킨다")
        void absoluteRefSurvives() throws Exception {
            long id = source();
            patchRow(id, 1, "[\"공책\",\"1200\",\"2\",\"={단가}1\"]");

            String before = rows(id);
            long imported = importAndGetId(export(id));
            String after = rows(imported);
            // 행 번호로 보이는 표시형이 같다 = 같은 행을 계속 가리킨다.
            assertThat(JsonPath.<String>read(after, "$.data.rows[1].formulas.c3"))
                    .isEqualTo(JsonPath.read(before, "$.data.rows[1].formulas.c3"));
            assertThat(JsonPath.<String>read(after, "$.data.rows[1].cells[3]")).isEqualTo("500");
        }

        @Test
        @DisplayName("푸터 요약은 행이 아니라 열 설정으로 돌아온다")
        void summaryReturnsAsColumnSetting() throws Exception {
            long id = source();
            mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/summary", id, "c1")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"summary\":\"SUM\"}"))
                    .andExpect(status().isOk());

            long imported = importAndGetId(export(id));

            String metaJson = meta(imported);
            assertThat(JsonPath.<String>read(metaJson, "$.data.columns[1].summary")).isEqualTo("SUM");
            // 요약줄이 데이터 행으로 딸려 들어오지 않았다.
            assertThat(JsonPath.<Integer>read(metaJson, "$.data.rowCount")).isEqualTo(2);
        }

        @Test
        @DisplayName("셀 배경·정렬과 병합이 그대로 온다")
        void stylesAndMerges() throws Exception {
            long id = source();
            mockMvc.perform(put("/api/datasets/{id}/rows/{i}/cells/{c}/style", id, 0, "c0")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bg\":\"yellow\",\"align\":\"center\"}"))
                    .andExpect(status().isOk());
            mockMvc.perform(put("/api/datasets/{id}/rows/{i}/cells/{c}/merge", id, 1, "c0")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rowSpan\":1,\"colSpan\":2}"))
                    .andExpect(status().isOk());

            long imported = importAndGetId(export(id));

            String rowsJson = rows(imported);
            assertThat(JsonPath.<String>read(rowsJson, "$.data.rows[0].styles.c0.bg"))
                    .isEqualTo("yellow");
            assertThat(JsonPath.<String>read(rowsJson, "$.data.rows[0].styles.c0.align"))
                    .isEqualTo("center");

            mockMvc.perform(get("/api/datasets/{id}/merges", imported)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.merges[0].rowIndex").value(1))
                    .andExpect(jsonPath("$.data.merges[0].colKey").value("c0"))
                    .andExpect(jsonPath("$.data.merges[0].colSpan").value(2));
        }

        @Test
        @DisplayName("서식을 안 준 셀은 서식 없이 온다 — 기본값이 서식으로 굳지 않는다")
        void untouchedCellsHaveNoStyle() throws Exception {
            long imported = importAndGetId(export(source()));
            String rowsJson = rows(imported);
            assertThat(JsonPath.<Object>read(rowsJson, "$.data.rows[0].styles"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("남의 파일")
    class ForeignFiles {

        @Test
        @DisplayName("첫 줄을 열 이름으로 쓴다")
        void firstRowBecomesHeader() throws Exception {
            byte[] file = workbook("성적", new String[][]{
                    {"과목", "점수"},
                    {"네트워크", "92"},
                    {"운영체제", "88"}});

            long imported = importAndGetId(file);

            assertThat(JsonPath.<String>read(meta(imported), "$.data.columns[0].label"))
                    .isEqualTo("과목");
            assertThat(JsonPath.<Integer>read(meta(imported), "$.data.rowCount")).isEqualTo(2);
        }

        @Test
        @DisplayName("첫 줄을 데이터로 쓰라고 하면 열 이름은 우리가 짓는다")
        void firstRowAsData() throws Exception {
            byte[] file = workbook("성적", new String[][]{
                    {"네트워크", "92"},
                    {"운영체제", "88"}});

            String body = importFile(file, "?firstRowAsHeader=false")
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            long imported = ((Number) JsonPath.read(body, "$.data.datasetId")).longValue();

            assertThat(JsonPath.<String>read(meta(imported), "$.data.columns[0].label"))
                    .isEqualTo("열 1");
            assertThat(JsonPath.<Integer>read(meta(imported), "$.data.rowCount")).isEqualTo(2);
        }

        @Test
        @DisplayName("옮길 수 없는 수식은 값으로 들어오고, 몇 개인지 말해 준다")
        void untranslatableFormulasBecomeValues() throws Exception {
            // 부분 범위는 우리 모델에 없다 — 열 전체가 아니면 담을 그릇이 없다.
            byte[] file = workbook("합계", new String[][]{
                    {"값", "누계"},
                    {"10", "=SUM(A2:A2)"},
                    {"20", "=A2*2"}});

            String body = importFile(file, "")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.formulasAsValue").value(1))
                    .andExpect(jsonPath("$.data.formulasImported").value(1))
                    .andReturn().getResponse().getContentAsString();

            long imported = ((Number) JsonPath.read(body, "$.data.datasetId")).longValue();
            String rowsJson = rows(imported);
            // 옮기지 못한 쪽은 수식이 아니라 값으로 남는다.
            assertThat(JsonPath.<Object>read(rowsJson, "$.data.rows[0].formulas"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .doesNotContainKey("c1");
        }

        @Test
        @DisplayName("여러 시트가 있으면 목록을 먼저 보여주고, 고른 시트만 들인다")
        void analyzeThenPickSheet() throws Exception {
            byte[] file;
            try (Workbook wb = new XSSFWorkbook();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet first = wb.createSheet("첫째");
                first.createRow(0).createCell(0).setCellValue("A");
                Sheet second = wb.createSheet("둘째");
                second.createRow(0).createCell(0).setCellValue("이름");
                second.createRow(1).createCell(0).setCellValue("값");
                wb.write(out);
                file = out.toByteArray();
            }

            mockMvc.perform(multipart("/api/datasets/import/analyze")
                            .file(new MockMultipartFile("file", "두장.xlsx", XLSX_TYPE, file))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("첫째"))
                    .andExpect(jsonPath("$.data[1].name").value("둘째"))
                    .andExpect(jsonPath("$.data[1].preview[0][0]").value("이름"));

            String body = importFile(file, "?sheet=둘째")
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            long imported = ((Number) JsonPath.read(body, "$.data.datasetId")).longValue();
            assertThat(JsonPath.<String>read(meta(imported), "$.data.columns[0].label"))
                    .isEqualTo("이름");
        }

        @Test
        @DisplayName("CSV도 같은 길로 들어온다 — 값만 있는 표가 된다")
        void csvGoesThroughTheSamePath() throws Exception {
            byte[] csv = "과목,점수\n네트워크,92\n운영체제,88\n"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);

            String body = mockMvc.perform(multipart("/api/datasets/import")
                            .file(new MockMultipartFile("file", "성적.csv", "text/csv", csv))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            long imported = ((Number) JsonPath.read(body, "$.data.datasetId")).longValue();
            assertThat(JsonPath.<String>read(meta(imported), "$.data.columns[0].label"))
                    .isEqualTo("과목");
            assertThat(JsonPath.<Integer>read(meta(imported), "$.data.rowCount")).isEqualTo(2);
            assertThat(JsonPath.<String>read(rows(imported), "$.data.rows[0].cells[1]"))
                    .isEqualTo("92");
        }

        @Test
        @DisplayName("따옴표 안의 쉼표는 값의 일부다")
        void csvQuotedComma() throws Exception {
            byte[] csv = "이름,메모\n연필,\"싸고, 흔하다\"\n"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);

            String body = mockMvc.perform(multipart("/api/datasets/import")
                            .file(new MockMultipartFile("file", "메모.csv", "text/csv", csv))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            long imported = ((Number) JsonPath.read(body, "$.data.datasetId")).longValue();
            assertThat(JsonPath.<String>read(rows(imported), "$.data.rows[0].cells[1]"))
                    .isEqualTo("싸고, 흔하다");
        }

        @Test
        @DisplayName(".xlsx도 .csv도 아니면 거절한다")
        void rejectsOtherFormats() throws Exception {
            mockMvc.perform(multipart("/api/datasets/import")
                            .file(new MockMultipartFile("file", "표.xls", "application/vnd.ms-excel",
                                    new byte[]{1, 2, 3}))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("로그인하지 않으면 들일 수 없다")
        void requiresAuth() throws Exception {
            mockMvc.perform(multipart("/api/datasets/import")
                            .file(new MockMultipartFile("file", "표.xlsx", XLSX_TYPE, new byte[]{1})))
                    .andExpect(status().isUnauthorized());
        }
    }
}
