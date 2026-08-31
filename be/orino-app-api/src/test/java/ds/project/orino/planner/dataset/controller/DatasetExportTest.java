package ds.project.orino.planner.dataset.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * .xlsx 내보내기(#1308 · Epic #892 c).
 *
 * <p>내려받은 바이트를 <b>POI로 다시 열어</b> 확인한다 — 응답이 200이고 길이가 0이 아니라는 것은
 * 「엑셀에서 열린다」를 보장하지 않는다. 특히 수식은 문자열로 써 넣는 것이라, 엑셀 문법이
 * 아니면 파일을 열 때 비로소 깨진다.
 */
class DatasetExportTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private long datasetId;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);

        String body = mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columns":[
                                  {"key":"c0","label":"품목"},
                                  {"key":"c1","label":"단가"},
                                  {"key":"c2","label":"수량"},
                                  {"key":"c3","label":"금액"}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        datasetId = ((Number) JsonPath.read(body, "$.data.id")).longValue();

        mockMvc.perform(patch("/api/datasets/{id}/name", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"주문 내역\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/datasets/{id}/rows/bulk", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[["연필","500","3",""],["공책","1200","2",""]]}
                                """))
                .andExpect(status().isOk());
    }

    private void patchRow(int rowIndex, String cells) throws Exception {
        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", datasetId, rowIndex)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":" + cells + "}"))
                .andExpect(status().isOk());
    }

    private byte[] download() throws Exception {
        return mockMvc.perform(get("/api/datasets/{id}/export", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private XSSFWorkbook open(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    @Test
    @DisplayName("헤더는 열 이름, 값은 숫자로 읽히면 숫자 셀로 나간다")
    void headerAndValues() throws Exception {
        try (XSSFWorkbook wb = open(download())) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("주문 내역");

            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("품목");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("금액");

            // 문자열은 문자열로, 숫자로 읽히는 값은 숫자로 — 엑셀에서 바로 계산에 쓰인다.
            assertThat(sheet.getRow(1).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("연필");
            assertThat(sheet.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(500.0);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("공책");
        }
    }

    @Test
    @DisplayName("수식은 값이 아니라 A1 수식으로 나간다 — 파일에서 이어서 계산된다")
    void formulasStayAlive() throws Exception {
        patchRow(0, "[\"연필\",\"500\",\"3\",\"={단가} * {수량}\"]");
        patchRow(1, "[\"공책\",\"1200\",\"2\",\"={단가} * {수량}\"]");

        try (XSSFWorkbook wb = open(download())) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(3).getCellType()).isEqualTo(CellType.FORMULA);
            // 같은 행 참조는 그 행을 따라간다 — 2행은 B2*C2, 3행은 B3*C3.
            assertThat(sheet.getRow(1).getCell(3).getCellFormula()).isEqualTo("(B2 * C2)");
            assertThat(sheet.getRow(2).getCell(3).getCellFormula()).isEqualTo("(B3 * C3)");

            // 문법이 맞는 것과 같은 값이 나오는 것은 다른 얘기다 — 실제로 계산해 본다.
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            assertThat(evaluator.evaluate(sheet.getRow(1).getCell(3)).getNumberValue())
                    .isEqualTo(1500.0);
            assertThat(evaluator.evaluate(sheet.getRow(2).getCell(3)).getNumberValue())
                    .isEqualTo(2400.0);
        }
    }

    @Test
    @DisplayName("특정 행을 가리키는 수식은 그 행의 실제 자리에 고정된 주소로 나간다")
    void absoluteRefPinsRow() throws Exception {
        patchRow(1, "[\"공책\",\"1200\",\"2\",\"={단가}1\"]");

        try (XSSFWorkbook wb = open(download())) {
            Sheet sheet = wb.getSheetAt(0);
            // 표의 1행 = 시트 2행. 행 고정이라 끌어 복사해도 안 따라 움직인다.
            assertThat(sheet.getRow(2).getCell(3).getCellFormula()).isEqualTo("B$2");
        }
    }

    @Test
    @DisplayName("푸터 요약이 있으면 마지막 행에 엑셀 집계 수식이 붙는다")
    void summaryRowIsAFormula() throws Exception {
        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/summary", datasetId, "c1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"SUM\"}"))
                .andExpect(status().isOk());

        try (XSSFWorkbook wb = open(download())) {
            Sheet sheet = wb.getSheetAt(0);
            // 데이터가 2행(시트 2~3행)이므로 요약은 시트 4행.
            assertThat(sheet.getRow(3).getCell(1).getCellFormula()).isEqualTo("SUM(B2:B3)");
            // 요약이 없는 열엔 아무것도 두지 않는다.
            assertThat(sheet.getRow(3).getCell(0)).isNull();
        }
    }

    @Test
    @DisplayName("셀 배경·정렬은 서식으로, 병합은 병합 영역으로 나간다")
    void stylesAndMerges() throws Exception {
        mockMvc.perform(put("/api/datasets/{id}/rows/{i}/cells/{c}/style", datasetId, 0, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bg\":\"yellow\",\"align\":\"center\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/datasets/{id}/rows/{i}/cells/{c}/merge", datasetId, 1, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowSpan\":1,\"colSpan\":2}"))
                .andExpect(status().isOk());

        try (XSSFWorkbook wb = open(download())) {
            Sheet sheet = wb.getSheetAt(0);

            XSSFCellStyle style = (XSSFCellStyle) sheet.getRow(1).getCell(0).getCellStyle();
            assertThat(style.getAlignment()).isEqualTo(HorizontalAlignment.CENTER);
            assertThat(style.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
            // 화면의 노랑을 sRGB로 굳힌 값 그대로.
            assertThat(style.getFillForegroundColorColor().getRGB())
                    .containsExactly((byte) 0xFC, (byte) 0xF2, (byte) 0xCD);

            assertThat(sheet.getNumMergedRegions()).isEqualTo(1);
            CellRangeAddress region = sheet.getMergedRegion(0);
            // 표 1행 c0에서 두 칸 — 시트에선 3행 A~B.
            assertThat(region.getFirstRow()).isEqualTo(2);
            assertThat(region.getLastRow()).isEqualTo(2);
            assertThat(region.getFirstColumn()).isZero();
            assertThat(region.getLastColumn()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("파일 이름은 표 이름에서 나오고 봉투 없이 첨부로 내려온다")
    void downloadsAsAttachment() throws Exception {
        mockMvc.perform(get("/api/datasets/{id}/export", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''")));
    }

    @Test
    @DisplayName("남의 표는 내보낼 수 없다")
    void othersDatasetIsNotFound() throws Exception {
        mockMvc.perform(get("/api/datasets/{id}/export", datasetId + 999))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/datasets/{id}/export", datasetId + 999)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("아는 형식이 아니면 거절한다")
    void unknownFormatRejected() throws Exception {
        mockMvc.perform(get("/api/datasets/{id}/export", datasetId)
                        .param("format", "pdf")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isBadRequest());
    }
}
