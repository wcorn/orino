package ds.project.orino.planner.travel.move.service;

import ds.project.orino.domain.planner.travel.entity.TravelMove;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.repository.TravelMoveRepository;
import ds.project.orino.planner.travel.move.dto.MoveResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 연속한 두 일정 사이 이동(§4.4).
 *
 * <p>일정 리스트에 항상 표시된다 — 현지에서 계획을 따라갈 수 있는지는 "다음 장소까지 어떻게,
 * 얼마나 걸리는지"가 가른다.
 *
 * <p><b>외부 호출이 없다</b>(#1208). 예전에는 직선거리로 도보/자동차를 판정하고 Google Routes로
 * 소요 시간을 사 왔다. 지금은 사용자가 적어 둔 값을 읽기만 한다 — 보드를 몇 번 열든 비용이
 * 늘지 않고, 비행기·신칸센처럼 앱이 답할 수 없던 이동도 그대로 실린다.
 */
@Service
public class MoveService {

    private final TravelMoveRepository moveRepository;

    public MoveService(TravelMoveRepository moveRepository) {
        this.moveRepository = moveRepository;
    }

    /**
     * 정렬된 일정 목록에서 이동을 만든다.
     *
     * <p><b>장소 없는 일정은 건너뛴다</b>(§4.4) — "점심"처럼 장소를 안 정한 일정이 사이에 끼어도
     * 앞뒤 장소 사이 이동은 여전히 알고 싶다. 그걸 끊으면 정작 필요한 이동이 사라진다.
     *
     * <p><b>아직 적지 않은 구간도 행으로 남긴다.</b> 빈 행이 곧 입력 지점이다 — 응답에서 빼면
     * 화면에 누를 자리가 없어진다.
     */
    public List<MoveResponse> moves(Long memberId, List<TripActivity> ordered) {
        List<Located> located = locate(ordered);
        Map<PlacePair, TravelMove> saved = savedMoves(memberId, pairsOf(located));

        List<MoveResponse> moves = new ArrayList<>();
        for (int i = 0; i + 1 < located.size(); i++) {
            Located from = located.get(i);
            Located to = located.get(i + 1);
            TravelMove move = saved.get(new PlacePair(from.placeId(), to.placeId()));
            moves.add(move == null
                    ? MoveResponse.emptyBetween(from.activityId(), to.activityId())
                    : MoveResponse.between(from.activityId(), to.activityId(), move));
        }
        return moves;
    }

    /**
     * 그날 <b>마지막 일정에서 어떤 장소까지</b>의 이동. 숙소 이동 행(§3.5)이 쓴다.
     *
     * <p>일정 사이 이동과 <b>같은 저장소</b>를 탄다 — 장소 쌍이 키라 숙소든 일정이든 두 장소를
     * 잇는 이동은 한 값이다. 도쿄역에서 숙소까지를 한 번 적어 두면 다른 날에도 그대로 뜬다.
     *
     * <p>마지막 일정에 장소가 없으면 <b>비어 있는 값</b>을 준다. 이동이 성립하지 않는 것을
     * 0분으로 답하면 화면이 "바로 옆"이라고 읽는다.
     */
    public Optional<MoveResponse> moveToPlace(Long memberId, List<TripActivity> ordered,
                                              Long stayId, Long destinationPlaceId) {
        if (destinationPlaceId == null) {
            return Optional.empty();
        }
        List<Located> located = locate(ordered);
        if (located.isEmpty()) {
            return Optional.empty();
        }
        Located from = located.get(located.size() - 1);
        return Optional.of(moveRepository
                .findByMemberIdAndFromPlaceIdAndToPlaceId(memberId, from.placeId(),
                        destinationPlaceId)
                .map(move -> MoveResponse.toStay(from.activityId(), stayId, move))
                .orElseGet(() -> MoveResponse.emptyToStay(from.activityId(), stayId)));
    }

    /**
     * 그날 <b>마지막 일정이 이미 그 장소인가.</b> 숙소 이동 행(§3.5)이 "이동이 있기는 한가"를
     * 묻는 자리다.
     *
     * <p>비교 대상은 마지막 일정이 아니라 <b>장소를 가진 마지막 일정</b>이다 — 이동이 출발지로
     * 삼는 것이 그 일정이라, 뒤에 장소 없는 일정("짐 정리")이 끼어도 판정이 밀리면 안 된다.
     *
     * <p>같은 장소끼리 이동 행을 그리면 <b>이미 그곳인 사람에게 이동하라고 말한다.</b>
     */
    public boolean alreadyAt(List<TripActivity> ordered, Long destinationPlaceId) {
        List<Located> located = locate(ordered);
        if (located.isEmpty() || destinationPlaceId == null) {
            return false;
        }
        return destinationPlaceId.equals(located.get(located.size() - 1).placeId());
    }

    /**
     * 출발 알림을 켤 수 있는 일정 id.
     *
     * <p>조건은 하나다 — <b>직전에 장소 있는 일정이 있는가.</b> 어디서 출발하는지 모르면 언제
     * 나서야 하는지도 모른다.
     *
     * <p>도시 경계 조건은 없다(#1208). 예전에는 도시를 넘는 구간을 계산하지 않아 알림 시각을
     * 정할 수 없었지만, 지금은 사용자가 적어 두면 신칸센 구간에도 출발 알림이 선다.
     *
     * <p><b>소요 시간이 아직 비어 있어도 켤 수 있다고 본다.</b> 스위치는 저장되는 설정이라,
     * 값을 안 적었다는 이유로 끄면 나중에 적어도 꺼진 채로 남는다.
     */
    public Set<Long> departureNotifiable(List<TripActivity> ordered) {
        List<Located> located = locate(ordered);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i + 1 < located.size(); i++) {
            ids.add(located.get(i + 1).activityId());
        }
        return ids;
    }

    /**
     * 장소를 가진 일정만 순서대로 남긴다.
     *
     * <p><b>좌표는 보지 않는다</b>(#1208). 예전에는 Routes에 넘길 좌표가 없으면 이동 자체를
     * 만들지 못해 직접 입력한 장소가 통째로 빠졌다. 지금 이동을 잇는 것은 장소 id이므로,
     * 검색에 안 나와 이름만 적어 둔 장소 사이에도 이동을 적을 수 있다.
     */
    private static List<Located> locate(List<TripActivity> ordered) {
        List<Located> located = new ArrayList<>();
        for (TripActivity activity : ordered) {
            if (activity.getPlaceId() != null) {
                located.add(new Located(activity.getId(), activity.getPlaceId()));
            }
        }
        return located;
    }

    private static List<PlacePair> pairsOf(List<Located> located) {
        List<PlacePair> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < located.size(); i++) {
            pairs.add(new PlacePair(located.get(i).placeId(), located.get(i + 1).placeId()));
        }
        return pairs;
    }

    /**
     * 그날 필요한 이동을 <b>한 번에</b> 읽는다. 구간마다 조회하면 일정 수만큼 쿼리가 나고,
     * 그건 유료 호출을 없애면서 DB 왕복으로 갈아탄 것일 뿐이다.
     *
     * <p>양쪽에 {@code IN}을 걸어 받아 온 뒤 실제 쌍만 남긴다 — 조합이 딸려 오지만 그날 장소는
     * 많아야 열몇 개다.
     */
    private Map<PlacePair, TravelMove> savedMoves(Long memberId, List<PlacePair> pairs) {
        if (pairs.isEmpty()) {
            return Map.of();
        }
        Set<Long> froms = new HashSet<>();
        Set<Long> tos = new HashSet<>();
        pairs.forEach(pair -> {
            froms.add(pair.fromPlaceId());
            tos.add(pair.toPlaceId());
        });

        Set<PlacePair> wanted = new HashSet<>(pairs);
        Map<PlacePair, TravelMove> byPair = new HashMap<>();
        for (TravelMove move : moveRepository
                .findAllByMemberIdAndFromPlaceIdInAndToPlaceIdIn(memberId, froms, tos)) {
            PlacePair pair = new PlacePair(move.getFromPlaceId(), move.getToPlaceId());
            if (wanted.contains(pair)) {
                byPair.put(pair, move);
            }
        }
        return byPair;
    }

    /** 이동 하나의 양 끝. 방향을 유지한다 — A→B와 B→A는 다른 이동이다. */
    private record PlacePair(Long fromPlaceId, Long toPlaceId) {
    }

    private record Located(Long activityId, Long placeId) {
    }
}
