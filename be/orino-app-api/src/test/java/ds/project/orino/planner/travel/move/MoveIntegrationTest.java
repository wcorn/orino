package ds.project.orino.planner.travel.move;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.StubExternalsConfig;
import ds.project.orino.support.TravelCityFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보드의 이동(§4.4, #1208). 사용자가 직접 적은 값을 그대로 보여준다 — <b>외부 호출이 없다.</b>
 *
 * <p>예전 판(자동 계산)에서 이 자리를 지키던 것은 "캐시가 유료 호출을 줄이는가"였다. 이제 그
 * 질문은 사라졌고, 대신 <b>적어 둔 값이 어디까지 따라오는가</b>를 고정한다 — 순서를 바꿔도,
 * 날짜가 달라도, 다른 여행에서도 같은 두 장소면 같은 이동이다.
 */
@Import(StubExternalsConfig.class)
class MoveIntegrationTest extends ApiTestSupport {

    private static final BigDecimal SENSOJI_LAT = new BigDecimal("35.7147651");
    private static final BigDecimal SENSOJI_LNG = new BigDecimal("139.7966553");
    private static final BigDecimal SKYTREE_LAT = new BigDecimal("35.7100627");
    private static final BigDecimal SKYTREE_LNG = new BigDecimal("139.8107004");
    private static final BigDecimal SHINJUKU_LAT = new BigDecimal("35.6895014");
    private static final BigDecimal SHINJUKU_LNG = new BigDecimal("139.6917337");

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TravelPlaceRepository placeRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private Long memberId;
    private long tripId;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        memberId = memberRepository.findAll().get(0).getId();
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        tripId = createTrip();
    }

    @Nested
    @DisplayName("어디에 이동 행이 생기나")
    class Which {

        @Test
        @DisplayName("장소가 있는 연속된 두 일정 사이에 생긴다 — 아직 안 적었어도 빈 행으로")
        void betweenConsecutivePlaces() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));

            // 빈 행이 곧 입력 지점이다 — 빼 버리면 화면에 누를 곳이 없어진다.
            board()
                    .andExpect(jsonPath("$.data.moves", hasSize(1)))
                    .andExpect(jsonPath("$.data.moves[0].mode").doesNotExist())
                    .andExpect(jsonPath("$.data.moves[0].durationMinutes").doesNotExist());
        }

        @Test
        @DisplayName("사이에 장소 없는 일정이 끼면 건너뛰고 잇는다")
        void skipsActivitiesWithoutPlace() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            // "점심"처럼 장소를 안 정한 일정이 있다고 앞뒤 이동을 잃으면 안 된다.
            addActivity("점심", null);
            addActivity("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));

            board().andExpect(jsonPath("$.data.moves", hasSize(1)));
        }

        @Test
        @DisplayName("장소가 하나뿐이면 이동 행이 없다")
        void noMoveWithSinglePlace() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("점심", null);

            board().andExpect(jsonPath("$.data.moves", hasSize(0)));
        }

        @Test
        @DisplayName("좌표 없는 장소(직접 입력)에도 이동을 적을 수 있다")
        void allowsPlacesWithoutCoordinates() throws Exception {
            // 자동 계산 시절에는 좌표가 없으면 Routes에 넘길 것이 없어 행 자체가 사라졌다.
            // 이동을 잇는 것이 장소 id인 지금은 검색에 안 나오는 곳도 이동의 끝이 된다.
            TravelPlace manual = placeRepository.save(TravelPlace.manual(memberId, "골목 카페"));
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("골목 카페", manual.getId());
            List<Integer> ids = activityIds();

            saveMove(ids.get(0), ids.get(1), """
                    "mode": "WALK", "durationMinutes": 7
                    """).andExpect(status().isOk());

            board()
                    .andExpect(jsonPath("$.data.moves", hasSize(1)))
                    .andExpect(jsonPath("$.data.moves[0].durationMinutes").value(7));
        }

        @Test
        @DisplayName("보관함에는 이동이 없다 — 순서에 이동 의미가 없다")
        void archiveHasNoMoves() throws Exception {
            addUnscheduled("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addUnscheduled("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));

            archiveBoard()
                    .andExpect(jsonPath("$.data.activities", hasSize(2)))
                    .andExpect(jsonPath("$.data.moves", hasSize(0)));
        }

        @Test
        @DisplayName("일정이 셋이면 이동은 둘 — 앞 이동의 도착이 뒤 이동의 출발이다")
        void chainsAcrossActivities() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));
            addActivity("신주쿠", place("신주쿠", SHINJUKU_LAT, SHINJUKU_LNG));

            String body = board()
                    .andExpect(jsonPath("$.data.moves", hasSize(2)))
                    .andReturn().getResponse().getContentAsString();

            List<Integer> froms = JsonPath.read(body, "$.data.moves[*].fromActivityId");
            List<Integer> tos = JsonPath.read(body, "$.data.moves[*].toActivityId");
            org.assertj.core.api.Assertions.assertThat(tos.get(0)).isEqualTo(froms.get(1));
        }
    }

    @Nested
    @DisplayName("적어 둔 이동")
    class Saving {

        @Test
        @DisplayName("수단·이름·시간·링크·메모가 그대로 실린다 — 무엇을 타는지가 핵심이다")
        void savesEverythingWeWrote() throws Exception {
            addActivity("나리타", place("나리타", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("도쿄역", place("도쿄역", SKYTREE_LAT, SKYTREE_LNG));
            List<Integer> ids = activityIds();

            saveMove(ids.get(0), ids.get(1), """
                    "mode": "TRAIN", "name": "나리타 익스프레스 3호", "durationMinutes": 53,
                    "url": "https://www.jreast.co.jp/", "memo": "5호차 12A"
                    """).andExpect(status().isOk());

            board()
                    .andExpect(jsonPath("$.data.moves[0].mode").value("TRAIN"))
                    .andExpect(jsonPath("$.data.moves[0].name").value("나리타 익스프레스 3호"))
                    .andExpect(jsonPath("$.data.moves[0].durationMinutes").value(53))
                    .andExpect(jsonPath("$.data.moves[0].url").value("https://www.jreast.co.jp/"))
                    .andExpect(jsonPath("$.data.moves[0].memo").value("5호차 12A"));
        }

        @Test
        @DisplayName("수단만 먼저 정하고 시간은 비워 둘 수 있다 — 확인은 나중에 한다")
        void allowsModeWithoutDuration() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));
            List<Integer> ids = activityIds();

            saveMove(ids.get(0), ids.get(1), "\"mode\": \"SUBWAY\"")
                    .andExpect(status().isOk());

            board()
                    .andExpect(jsonPath("$.data.moves[0].mode").value("SUBWAY"))
                    .andExpect(jsonPath("$.data.moves[0].durationMinutes").doesNotExist());
        }

        @Test
        @DisplayName("같은 구간에 다시 저장하면 덮어쓴다 — 한 구간에 이동은 하나다")
        void overwritesSameLeg() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));
            List<Integer> ids = activityIds();

            saveMove(ids.get(0), ids.get(1), "\"mode\": \"WALK\", \"durationMinutes\": 20");
            saveMove(ids.get(0), ids.get(1), "\"mode\": \"BUS\", \"durationMinutes\": 8");

            board()
                    .andExpect(jsonPath("$.data.moves", hasSize(1)))
                    .andExpect(jsonPath("$.data.moves[0].mode").value("BUS"))
                    .andExpect(jsonPath("$.data.moves[0].durationMinutes").value(8));
        }

        @Test
        @DisplayName("도시를 넘는 구간에도 적을 수 있다 — 자동 계산이 못 하던 바로 그 자리다")
        void allowsAcrossCities() throws Exception {
            // 예전에는 도시 경계를 넘으면 계산 자체를 하지 않아 값이 비었다(§3.4).
            // 정작 미리 정해 두는 이동이 신칸센·비행기 구간이다.
            addActivity("오사카 가게", placeIn("오사카 가게", "ChIJ_osaka",
                    SENSOJI_LAT, SENSOJI_LNG));
            addActivity("교토 가게", placeIn("교토 가게", "ChIJ_kyoto",
                    SKYTREE_LAT, SKYTREE_LNG));
            List<Integer> ids = activityIds();

            saveMove(ids.get(0), ids.get(1), """
                    "mode": "TRAIN", "name": "특급 하루카", "durationMinutes": 75
                    """).andExpect(status().isOk());

            board()
                    .andExpect(jsonPath("$.data.moves[0].name").value("특급 하루카"))
                    .andExpect(jsonPath("$.data.moves[0].durationMinutes").value(75));
        }

        @Test
        @DisplayName("순서를 바꿔도 살아 있다 — 장소 쌍에 저장하는 이유가 이것이다")
        void survivesReorder() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));
            addActivity("신주쿠", place("신주쿠", SHINJUKU_LAT, SHINJUKU_LNG));
            List<Integer> ids = activityIds();

            // 스카이트리 → 신주쿠 구간에만 적어 둔다.
            saveMove(ids.get(1), ids.get(2), "\"mode\": \"SUBWAY\", \"durationMinutes\": 31");

            // 앞의 두 일정을 뒤집어도 스카이트리→신주쿠는 그대로 이어진다.
            reorder(ids.get(1), ids.get(0), ids.get(2));

            board()
                    .andExpect(jsonPath("$.data.moves[1].fromActivityId").value(ids.get(0)))
                    .andExpect(jsonPath("$.data.moves[1].toActivityId").value(ids.get(2)))
                    .andExpect(jsonPath("$.data.moves[1].durationMinutes").doesNotExist());
            // 적어 둔 구간(스카이트리→신주쿠)은 사라지지 않았다 — 다시 이으면 그대로 나온다.
            reorder(ids.get(0), ids.get(1), ids.get(2));
            board().andExpect(jsonPath("$.data.moves[1].durationMinutes").value(31));
        }

        @Test
        @DisplayName("방향이 반대면 다른 이동이다 — 편도 항공과 일방통행이 실제로 다르다")
        void directionMatters() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));
            List<Integer> ids = activityIds();

            saveMove(ids.get(0), ids.get(1), "\"mode\": \"WALK\", \"durationMinutes\": 20");
            reorder(ids.get(1), ids.get(0));

            board().andExpect(jsonPath("$.data.moves[0].mode").doesNotExist());
        }

        @Test
        @DisplayName("다른 여행이 같은 두 장소를 이어도 그대로 뜬다 — 다시 적지 않아도 된다")
        void sharedAcrossTrips() throws Exception {
            Long from = place("센소지", SENSOJI_LAT, SENSOJI_LNG);
            Long to = place("스카이트리", SKYTREE_LAT, SKYTREE_LNG);
            addActivity("센소지", from);
            addActivity("스카이트리", to);
            List<Integer> ids = activityIds();
            saveMove(ids.get(0), ids.get(1), """
                    "mode": "SUBWAY", "name": "긴자선", "durationMinutes": 18
                    """);

            tripId = createTrip();
            addActivity("센소지", from);
            addActivity("스카이트리", to);

            board()
                    .andExpect(jsonPath("$.data.moves[0].name").value("긴자선"))
                    .andExpect(jsonPath("$.data.moves[0].durationMinutes").value(18));
        }
    }

    @Nested
    @DisplayName("지우기")
    class Deleting {

        @Test
        @DisplayName("지우면 빈 행으로 돌아간다 — 행까지 사라지면 다시 적을 곳이 없다")
        void deleteLeavesEmptyRow() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));
            List<Integer> ids = activityIds();
            saveMove(ids.get(0), ids.get(1), "\"mode\": \"WALK\", \"durationMinutes\": 20");

            deleteMove(ids.get(0), ids.get(1)).andExpect(status().isOk());

            board()
                    .andExpect(jsonPath("$.data.moves", hasSize(1)))
                    .andExpect(jsonPath("$.data.moves[0].mode").doesNotExist());
        }

        @Test
        @DisplayName("없는 이동을 지워도 성공이다 — 두 번 눌러 실패하면 안 지워진 줄 안다")
        void deleteIsIdempotent() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));
            List<Integer> ids = activityIds();

            deleteMove(ids.get(0), ids.get(1)).andExpect(status().isOk());
            deleteMove(ids.get(0), ids.get(1)).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("거절하는 요청")
    class Rejects {

        @Test
        @DisplayName("장소 없는 일정은 이동의 끝이 될 수 없다 — 400")
        void rejectsActivityWithoutPlace() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("점심", null);
            List<Integer> ids = activityIds();

            saveMove(ids.get(0), ids.get(1), "\"mode\": \"WALK\"")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-009"));
        }

        @Test
        @DisplayName("같은 장소끼리는 이동이 아니다 — 400")
        void rejectsSamePlace() throws Exception {
            Long same = place("센소지", SENSOJI_LAT, SENSOJI_LNG);
            addActivity("센소지", same);
            addActivity("센소지 다시", same);
            List<Integer> ids = activityIds();

            saveMove(ids.get(0), ids.get(1), "\"mode\": \"WALK\"")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-009"));
        }

        @Test
        @DisplayName("도착지가 없거나 둘이면 어디로 가는 이동인지 알 수 없다 — 400")
        void rejectsAmbiguousDestination() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            List<Integer> ids = activityIds();

            mockMvc.perform(put("/api/travel/trips/" + tripId + "/moves")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fromActivityId": %d, "mode": "WALK"}
                                    """.formatted(ids.get(0))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-009"));
        }

        @Test
        @DisplayName("이동수단을 고르지 않으면 400 — 분류가 없으면 화면이 그릴 것이 없다")
        void rejectsMissingMode() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));
            List<Integer> ids = activityIds();

            saveMove(ids.get(0), ids.get(1), "\"durationMinutes\": 10")
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("0분은 이동이 아니라 오타에 가깝다 — 400")
        void rejectsZeroDuration() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));
            List<Integer> ids = activityIds();

            saveMove(ids.get(0), ids.get(1), "\"mode\": \"WALK\", \"durationMinutes\": 0")
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("남의 여행은 404")
        void rejectsOtherMembersTrip() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));
            List<Integer> ids = activityIds();

            memberRepository.save(MemberFixture.create("other", "password"));
            String otherAuth = "Bearer "
                    + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

            mockMvc.perform(put("/api/travel/trips/" + tripId + "/moves")
                            .header(HttpHeaders.AUTHORIZATION, otherAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fromActivityId": %d, "toActivityId": %d, "mode": "WALK"}
                                    """.formatted(ids.get(0), ids.get(1))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("출발 알림을 켤 수 있는가")
    class DepartureNotifiable {

        @Test
        @DisplayName("도시를 넘어 들어오는 일정도 켤 수 있다 — 이제 사용자가 시간을 안다")
        void allowedAcrossCities() throws Exception {
            // 자동 계산 시절에는 도시를 넘으면 소요 시간을 못 구해 스위치를 막았다.
            addActivity("오사카 가게", placeIn("오사카 가게", "ChIJ_osaka",
                    SENSOJI_LAT, SENSOJI_LNG));
            addActivity("교토 가게", placeIn("교토 가게", "ChIJ_kyoto",
                    SKYTREE_LAT, SKYTREE_LNG));

            board().andExpect(jsonPath("$.data.activities[1].canDepartureNotify").value(true));
        }

        @Test
        @DisplayName("그날 첫 일정은 켤 수 없다 — 어디서 출발하는지가 없다")
        void blockedForFirstActivity() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));

            board().andExpect(jsonPath("$.data.activities[0].canDepartureNotify").value(false));
        }

        @Test
        @DisplayName("상세 응답도 보드와 같은 답을 준다 — 스위치가 있는 화면이 상세다")
        void detailAgreesWithBoard() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("점심", null);
            List<Integer> ids = activityIds();

            mockMvc.perform(get("/api/travel/activities/" + ids.get(1))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.canDepartureNotify").value(false));
        }

        @Test
        @DisplayName("보관함 일정은 켤 수 없다 — 날짜가 없어 알림 시각 자체가 서지 않는다")
        void blockedInArchive() throws Exception {
            addUnscheduled("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addUnscheduled("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));

            archiveBoard()
                    .andExpect(jsonPath("$.data.activities[1].canDepartureNotify").value(false));
        }
    }

    @Nested
    @DisplayName("순서 변경 응답")
    class Reorder {

        @Test
        @DisplayName("다시 이어진 구간의 이동을 함께 돌려준다 — 드래그는 손을 뗀 순간 보여야 한다")
        void returnsMovesAfterReorder() throws Exception {
            addActivity("센소지", place("센소지", SENSOJI_LAT, SENSOJI_LNG));
            addActivity("스카이트리", place("스카이트리", SKYTREE_LAT, SKYTREE_LNG));
            List<Integer> ids = activityIds();

            reorder(ids.get(1), ids.get(0))
                    .andExpect(jsonPath("$.data.moves", hasSize(1)))
                    .andExpect(jsonPath("$.data.moves[0].fromActivityId").value(ids.get(1)))
                    .andExpect(jsonPath("$.data.moves[0].toActivityId").value(ids.get(0)));
        }

        @Test
        @DisplayName("이동이 없어도 빈 배열로 온다")
        void emptyWhenNoMoves() throws Exception {
            addActivity("점심", null);
            addActivity("저녁", null);
            List<Integer> ids = activityIds();

            reorder(ids.get(1), ids.get(0))
                    .andExpect(jsonPath("$.data.moves", hasSize(0)));
        }
    }

    // --- 헬퍼 -------------------------------------------------------------

    private ResultActions saveMove(int from, int to, String fields) throws Exception {
        return mockMvc.perform(put("/api/travel/trips/" + tripId + "/moves")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fromActivityId": %d, "toActivityId": %d, %s}
                        """.formatted(from, to, fields)));
    }

    private ResultActions deleteMove(int from, int to) throws Exception {
        return mockMvc.perform(delete("/api/travel/trips/" + tripId + "/moves")
                .param("from", String.valueOf(from))
                .param("to", String.valueOf(to))
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    private ResultActions board() throws Exception {
        return mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .param("date", "2026-10-24")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    private ResultActions archiveBoard() throws Exception {
        return mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .param("archive", "true")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    private ResultActions reorder(int... ids) throws Exception {
        String list = java.util.Arrays.stream(ids).mapToObj(String::valueOf)
                .collect(java.util.stream.Collectors.joining(", "));
        return mockMvc.perform(put("/api/travel/trips/" + tripId + "/activities/order")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moves": [{"date": "2026-10-24", "activityIds": [%s]}]}
                                """.formatted(list)))
                .andExpect(status().isOk());
    }

    private Long place(String name, BigDecimal lat, BigDecimal lng) {
        TravelPlace place = placeRepository.save(
                TravelPlace.fromGoogle(memberId, "g-" + name + "-" + UUID.randomUUID(), name));
        place.updateBasics(name + " 주소", lat, lng, null, null);
        return placeRepository.saveAndFlush(place).getId();
    }

    /** 도시 식별자를 가진 장소. 도시 판정은 좌표가 아니라 이 값으로만 한다(D-23). */
    private Long placeIn(String name, String cityPlaceRef, BigDecimal lat, BigDecimal lng) {
        TravelPlace place = placeRepository.findById(place(name, lat, lng)).orElseThrow();
        place.updateCityInfo(cityPlaceRef, cityPlaceRef, "JP");
        return placeRepository.saveAndFlush(place).getId();
    }

    private long createTrip() throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "도쿄",
                                 "startDate": "2026-10-24", "endDate": "2026-10-27",
                                 %s}
                                """.formatted(TravelCityFixture.singleLeg(cityId("도쿄"), 4))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long cityId(String name) throws Exception {
        return TravelCityFixture.createCity(mockMvc, authHeader, name, "Asia/Tokyo", "JPY");
    }

    private void addActivity(String title, Long placeId) throws Exception {
        addActivity(title, placeId, "\"2026-10-24\"");
    }

    private void addUnscheduled(String title, Long placeId) throws Exception {
        addActivity(title, placeId, "null");
    }

    private void addActivity(String title, Long placeId, String date) throws Exception {
        String place = placeId == null ? "" : ", \"placeId\": %d".formatted(placeId);
        mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "activityDate": %s%s}
                                """.formatted(title, date, place)))
                .andExpect(status().isOk());
    }

    private List<Integer> activityIds() throws Exception {
        String body = board().andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.activities[*].id");
    }
}
