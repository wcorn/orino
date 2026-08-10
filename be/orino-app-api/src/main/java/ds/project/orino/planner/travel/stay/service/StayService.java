package ds.project.orino.planner.travel.stay.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripStay;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.domain.planner.travel.repository.TripStayRepository;
import ds.project.orino.planner.travel.stay.dto.StayOverlapResponse;
import ds.project.orino.planner.travel.stay.dto.StayRequest;
import ds.project.orino.planner.travel.stay.dto.StayResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 숙소 CRUD(§4.5).
 *
 * <p>숙소는 <b>기준 도시와 분리된 별도 엔티티다</b> — "오늘 있는 도시"와 "오늘 자는 곳"이 다를
 * 수 있다. 닛코 당일치기 날의 기준 도시는 닛코지만 자는 곳은 도쿄다. 그래서 날짜에 매달지
 * 않고 기간으로 들고, 어느 날짜에 붙는지는 조회할 때 파생한다.
 *
 * <p><b>겹치는 기간은 저장하지 않는다.</b> 겹침을 허용하면 "오늘 밤 어디서 자는가"에 답이 둘이
 * 되고, 화면은 그중 하나를 임의로 고를 수밖에 없다. 대신 거절할 때 <b>어느 숙소와 겹치는지</b>를
 * 함께 돌려준다 — "겹칩니다"만 말하면 사용자가 할 수 있는 일이 없다.
 */
@Service
@Transactional(readOnly = true)
public class StayService {

    private final TripRepository tripRepository;
    private final TripStayRepository stayRepository;
    private final TravelPlaceRepository placeRepository;

    public StayService(TripRepository tripRepository,
                       TripStayRepository stayRepository,
                       TravelPlaceRepository placeRepository) {
        this.tripRepository = tripRepository;
        this.stayRepository = stayRepository;
        this.placeRepository = placeRepository;
    }

    public List<StayResponse> list(Long memberId, Long tripId) {
        getOwnedTrip(memberId, tripId);
        return stayRepository.findAllByTripIdOrderByCheckInDateAscIdAsc(tripId).stream()
                .map(StayResponse::from)
                .toList();
    }

    @Transactional
    public StayResponse create(Long memberId, Long tripId, StayRequest request) {
        Trip trip = getOwnedTrip(memberId, tripId);
        validate(trip, request, memberId);
        requireNoOverlap(tripId, request, null);

        TripStay stay = new TripStay(tripId, request.name().trim(),
                request.checkInDate(), request.checkOutDate());
        stay.updateBasics(request.name().trim(), request.placeId(),
                request.checkInDate(), request.checkOutDate());
        stay.updateDetails(request.checkInTime(), request.checkOutTime(),
                request.bookingUrl(), request.memo());
        return StayResponse.from(stayRepository.save(stay));
    }

    @Transactional
    public StayResponse update(Long memberId, Long stayId, StayRequest request) {
        TripStay stay = getOwnedStay(memberId, stayId);
        Trip trip = getOwnedTrip(memberId, stay.getTripId());
        validate(trip, request, memberId);
        // 자기 자신과는 겹칠 수 없다 — 기간을 그대로 두고 이름만 고치는 요청이 막히면 안 된다.
        requireNoOverlap(stay.getTripId(), request, stayId);

        stay.updateBasics(request.name().trim(), request.placeId(),
                request.checkInDate(), request.checkOutDate());
        stay.updateDetails(request.checkInTime(), request.checkOutTime(),
                request.bookingUrl(), request.memo());
        return StayResponse.from(stayRepository.saveAndFlush(stay));
    }

    /**
     * 숙소만 지운다. <b>{@code 일정으로 추가}로 만든 일정은 그대로 둔다</b> — 사용자가 직접
     * 만든 일정이라, 숙소를 지웠다고 함께 사라지면 모르는 사이에 계획이 비는 셈이다.
     */
    @Transactional
    public void delete(Long memberId, Long stayId) {
        stayRepository.delete(getOwnedStay(memberId, stayId));
    }

    // ---------------- helpers ----------------

    private Trip getOwnedTrip(Long memberId, Long tripId) {
        return tripRepository.findByIdAndMemberId(tripId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_TRIP_NOT_FOUND));
    }

    /** 남의 숙소도 404 — 403이면 "그 id의 숙소는 존재한다"가 새어나간다. */
    private TripStay getOwnedStay(Long memberId, Long stayId) {
        TripStay stay = stayRepository.findById(stayId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_STAY_NOT_FOUND));
        tripRepository.findByIdAndMemberId(stay.getTripId(), memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_STAY_NOT_FOUND));
        return stay;
    }

    /**
     * 하루도 안 묵는 숙소와 기간 밖 숙소를 막는다.
     *
     * <p>기간 밖을 막는 이유는 <b>붙을 날짜가 없기 때문이다</b> — 저장은 되지만 어느 날짜 탭에도
     * 나타나지 않아, 사용자에게는 저장이 실패한 것과 구별되지 않는다.
     */
    private void validate(Trip trip, StayRequest request, Long memberId) {
        if (!request.checkInDate().isBefore(request.checkOutDate())) {
            throw new CustomException(ErrorCode.TRAVEL_INVALID_PERIOD);
        }
        if (!trip.covers(request.checkInDate()) || !trip.covers(request.checkOutDate())) {
            throw new CustomException(ErrorCode.TRAVEL_DATE_OUT_OF_RANGE);
        }
        if (request.placeId() != null
                && placeRepository.findByIdAndMemberId(request.placeId(), memberId).isEmpty()) {
            throw new CustomException(ErrorCode.TRAVEL_PLACE_NOT_FOUND);
        }
    }

    /**
     * 묵는 밤이 겹치는 숙소가 있으면 409. {@code [in, out)} 반열린 구간이라
     * <b>체크아웃일과 다음 체크인일이 같은 것은 겹침이 아니다</b> — 그게 이동일의 정상 모양이다.
     */
    private void requireNoOverlap(Long tripId, StayRequest request, Long excludeStayId) {
        LocalDate in = request.checkInDate();
        LocalDate out = request.checkOutDate();
        stayRepository.findAllByTripIdOrderByCheckInDateAscIdAsc(tripId).stream()
                .filter(stay -> !stay.getId().equals(excludeStayId))
                .filter(stay -> stay.overlaps(in, out))
                .findFirst()
                .ifPresent(conflict -> {
                    throw CustomException.withData(ErrorCode.TRAVEL_STAY_OVERLAP,
                            StayOverlapResponse.from(conflict));
                });
    }
}
