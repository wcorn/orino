package ds.project.orino.planner.travel.day.service;

import ds.project.orino.domain.planner.travel.entity.TripStay;
import ds.project.orino.domain.planner.travel.repository.TripStayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 여행 기간이 줄 때 걸쳐 있던 숙소를 정리한다.
 *
 * <p>기간 밖으로 밀려난 체크아웃일을 <b>새 종료일로 당긴다.</b> 그냥 두면 여행이 끝난 뒤까지
 * 묵는 숙소가 남아 "오늘 밤 자는 곳"이 기간 밖 날짜에도 잡힌다.
 *
 * <p>당긴 결과 묵는 밤이 하나도 없어지면({@code in >= out}) 숙소를 지운다. 0박짜리 숙소를
 * 남겨두면 목록에는 있는데 어느 날짜에도 안 붙는 유령이 된다.
 */
@Service
public class TripStayShrinkService {

    private final TripStayRepository stayRepository;

    public TripStayShrinkService(TripStayRepository stayRepository) {
        this.stayRepository = stayRepository;
    }

    /** 실제로 바꾸지 않고 몇 건이 줄고 몇 건이 사라지는지만 센다(확인 모달용). */
    public Impact preview(Long tripId, LocalDate newEndDate) {
        long shrunk = 0;
        long removed = 0;
        for (TripStay stay : affected(tripId, newEndDate)) {
            if (!stay.getCheckInDate().isBefore(newEndDate)) {
                removed++;
            } else {
                shrunk++;
            }
        }
        return new Impact(shrunk, removed);
    }

    @Transactional
    public Impact apply(Long tripId, LocalDate newEndDate) {
        long shrunk = 0;
        long removed = 0;
        for (TripStay stay : affected(tripId, newEndDate)) {
            stay.shrinkCheckOutTo(newEndDate);
            if (stay.isEmptyPeriod()) {
                stayRepository.delete(stay);
                removed++;
            } else {
                shrunk++;
            }
        }
        return new Impact(shrunk, removed);
    }

    /** 체크아웃일이 새 종료일보다 뒤인 숙소만 영향을 받는다. */
    private List<TripStay> affected(Long tripId, LocalDate newEndDate) {
        return stayRepository.findAllByTripIdOrderByCheckInDateAscIdAsc(tripId).stream()
                .filter(stay -> stay.getCheckOutDate().isAfter(newEndDate))
                .toList();
    }

    /** @param shrunkCount 기간만 줄어든 숙소 · @param removedCount 묵는 밤이 없어져 지워진 숙소 */
    public record Impact(long shrunkCount, long removedCount) {
    }
}
