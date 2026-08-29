package ds.project.orino.planner.travel.photo;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.repository.TripActivityLogRepository;
import ds.project.orino.domain.planner.travel.repository.TripActivityPhotoRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClock;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.TravelCityFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기록 사진 통합 테스트(§S-07).
 *
 * <p><b>MinIO를 건드리지 않는 경로만 다룬다.</b> presigned 발급은 순수 로컬 서명이고, 오브젝트
 * 삭제(best-effort)는 {@link TravelPhotoStorageServiceTest}가 단위로 고정한다 — 통합 테스트에서
 * 외부 호출을 하면 네트워크에 흔들린다.
 *
 * <p>고정 시각 {@code 2026-01-15T02:00Z}. 진행 중 여행(1/10~1/20)과 예정 여행(10/24~)을 함께 둔다.
 */
@FixedClock
class TravelPhotoIntegrationTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TripActivityPhotoRepository photoRepository;
    @Autowired
    private TripActivityLogRepository logRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private long activityId;
    private long upcomingActivityId;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);

        activityId = createActivity(createTrip("2026-01-10", "2026-01-20"), "센소지", "2026-01-15");
        upcomingActivityId =
                createActivity(createTrip("2026-10-24", "2026-10-27"), "시부야", "2026-10-24");
    }

    @Nested
    @DisplayName("POST /activities/{id}/photos/upload-url")
    class UploadUrl {

        @Test
        @DisplayName("원본과 썸네일 key가 갈린 presigned URL을 발급한다")
        void issuesPresignedUrl() throws Exception {
            mockMvc.perform(post("/api/travel/activities/" + activityId + "/photos/upload-url")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"contentType": "image/jpeg", "kind": "ORIGINAL"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.objectKey")
                            .value(org.hamcrest.Matchers.startsWith(
                                    "travel/activities/" + activityId + "/")))
                    .andExpect(jsonPath("$.data.uploadUrl")
                            .value(org.hamcrest.Matchers.containsString("X-Amz-Signature")));
        }

        @Test
        @DisplayName("JPEG이 아니면 400 — EXIF는 canvas 재인코딩으로 떨어진 상태여야 한다")
        void rejectsNonJpeg() throws Exception {
            mockMvc.perform(post("/api/travel/activities/" + activityId + "/photos/upload-url")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"contentType": "image/heic", "kind": "ORIGINAL"}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("여행 시작 전에는 발급하지 않는다 — 어차피 등록이 거부될 사진이다")
        void rejectsBeforeTripStart() throws Exception {
            mockMvc.perform(post("/api/travel/activities/" + upcomingActivityId
                            + "/photos/upload-url")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"contentType": "image/jpeg", "kind": "ORIGINAL"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-012"));
        }
    }

    @Nested
    @DisplayName("POST /activities/{id}/photos")
    class Register {

        @Test
        @DisplayName("메타를 등록하면 업로드 순서대로 돌려준다")
        void registersInOrder() throws Exception {
            registerPhotos(photoJson("a"), photoJson("b"))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].url").value(
                            "https://img.orino.dev/note-images/travel/activities/1/a.jpg"))
                    .andExpect(jsonPath("$.data[0].thumbUrl").value(
                            "https://img.orino.dev/note-images/travel/thumbs/1/a.jpg"));
        }

        @Test
        @DisplayName("평점·메모 없이 사진만 올려도 된다 — 기록이 없으면 만들어 붙인다")
        void createsLogWhenAbsent() throws Exception {
            assertThat(logRepository.findByActivityId(activityId)).isEmpty();

            registerPhotos(photoJson("a"));

            assertThat(logRepository.findByActivityId(activityId)).isPresent();
            mockMvc.perform(get("/api/travel/activities/" + activityId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.hasLog").value(true))
                    .andExpect(jsonPath("$.data.log.photos", hasSize(1)));
        }

        @Test
        @DisplayName("나눠 올려도 뒤에 붙는다 — 재시도가 앞 사진을 밀어내지 않는다")
        void appendsAcrossRequests() throws Exception {
            registerPhotos(photoJson("a"));
            registerPhotos(photoJson("b"))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].url")
                            .value(org.hamcrest.Matchers.containsString("/a.jpg")))
                    .andExpect(jsonPath("$.data[1].url")
                            .value(org.hamcrest.Matchers.containsString("/b.jpg")));
        }

        @Test
        @DisplayName("10장을 넘기면 400 — 기존 장수까지 합쳐서 센다")
        void rejectsOverLimit() throws Exception {
            registerPhotos(IntStream.range(0, 8)
                    .mapToObj(i -> photoJson("p" + i))
                    .toArray(String[]::new));

            mockMvc.perform(post("/api/travel/activities/" + activityId + "/photos")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"photos": [%s, %s, %s]}
                                    """.formatted(photoJson("x"), photoJson("y"), photoJson("z"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-013"));

            // 한 장도 들어가지 않는다 — 부분 반영이면 화면과 서버 장수가 어긋난다.
            assertThat(photoRepository.count()).isEqualTo(8);
        }

        @Test
        @DisplayName("썸네일이 없어도 등록된다 — 썸네일만 실패할 수 있다")
        void allowsMissingThumb() throws Exception {
            registerPhotos("""
                    {"objectKey": "travel/activities/1/a.jpg", "width": 4032, "height": 3024}
                    """)
                    .andExpect(jsonPath("$.data[0].thumbUrl").doesNotExist());
        }

        @Test
        @DisplayName("빈 배열은 400 — 보낼 게 없으면 요청 자체를 하지 않는다")
        void rejectsEmpty() throws Exception {
            mockMvc.perform(post("/api/travel/activities/" + activityId + "/photos")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"photos": []}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("남의 일정에는 등록할 수 없다 — 404로 존재조차 흘리지 않는다")
        void rejectsForeignActivity() throws Exception {
            memberRepository.save(MemberFixture.create("other", "password"));
            String otherHeader =
                    "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

            mockMvc.perform(post("/api/travel/activities/" + activityId + "/photos")
                            .header(HttpHeaders.AUTHORIZATION, otherHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"photos\": [%s]}".formatted(photoJson("a"))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("기록과의 관계")
    class WithLog {

        @Test
        @DisplayName("평점·메모를 다 지워도 사진이 있으면 기록이 남는다 — 지우면 사진까지 날아간다")
        void keepsLogWhenPhotosRemain() throws Exception {
            mockMvc.perform(put("/api/travel/activities/" + activityId + "/log")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"rating": 4, "memo": "좋았다"}
                                    """))
                    .andExpect(status().isOk());
            registerPhotos(photoJson("a"));

            mockMvc.perform(put("/api/travel/activities/" + activityId + "/log")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"rating": null, "memo": ""}
                                    """))
                    .andExpect(status().isOk())
                    // 평점·메모는 비었지만 사진이 남아 기록 자체는 살아 있다.
                    .andExpect(jsonPath("$.data.rating").doesNotExist())
                    .andExpect(jsonPath("$.data.photos", hasSize(1)));

            assertThat(photoRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("보드 목록에도 사진이 실린다 — 보드 응답이 곧 오프라인 캐시다")
        void boardCarriesPhotos() throws Exception {
            registerPhotos(photoJson("a"));

            mockMvc.perform(get("/api/travel/trips/" + tripOf(activityId) + "/board")
                            .param("date", "2026-01-15")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.activities[0].log.photos", hasSize(1)));
        }
    }

    // ---------------- helpers ----------------

    private static String photoJson(String name) {
        return """
                {"objectKey": "travel/activities/1/%s.jpg",
                 "thumbKey": "travel/thumbs/1/%s.jpg",
                 "width": 4032, "height": 3024}
                """.formatted(name, name);
    }

    private org.springframework.test.web.servlet.ResultActions registerPhotos(String... photos)
            throws Exception {
        String body = "{\"photos\": [%s]}".formatted(
                java.util.Arrays.stream(photos).collect(Collectors.joining(",")));
        return mockMvc.perform(post("/api/travel/activities/" + activityId + "/photos")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private long createTrip(String startDate, String endDate) throws Exception {
        long cityId = TravelCityFixture.createCity(mockMvc, authHeader, "도쿄",
                "Asia/Tokyo", "JPY");
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "도쿄",
                                 "startDate": "%s", "endDate": "%s",
                                 %s}
                                """.formatted(startDate, endDate,
                                        TravelCityFixture.singleLeg(cityId, 1))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long createActivity(long tripId, String title, String date) throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "activityDate": "%s"}
                                """.formatted(title, date)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long tripOf(long id) throws Exception {
        String body = mockMvc.perform(get("/api/travel/activities/" + id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.tripId")).longValue();
    }
}
