package ds.project.orino.planner.travel.expense.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.expense.dto.ExpenseAttachResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 여행에 지출을 붙이고 뗀다(명세 v2.2 §18 · D-27).
 *
 * <p><b>여기가 여행 쪽에 있는 이유.</b> 원장은 가계부 하나뿐이고 여행은 그 위의 읽기 뷰지만,
 * 「어느 여행의 지출인가」를 정하는 것은 여행의 일이다. 이 동작을 가계부에 두면 가계부가
 * 여행의 존재와 소유권을 알아야 하고, 그때부터 의존이 양방향이 된다.
 *
 * <p>의존은 <b>여행 → 가계부</b> 한 방향이다. 여기서 원장을 읽고 쓰지만 그 반대는 없다.
 *
 * <p>거래를 만들거나 지우지 않는다. 이 클래스가 건드리는 것은 {@code trip_id} 한 칸뿐이라,
 * 잘못 붙였어도 되돌리면 원래대로다 — 그래서 확인 절차를 두지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class TravelExpenseService {

    private final TripRepository tripRepository;
    private final LedgerTransactionRepository transactionRepository;

    public TravelExpenseService(TripRepository tripRepository,
                                LedgerTransactionRepository transactionRepository) {
        this.tripRepository = tripRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * 고른 거래를 이 여행에 붙인다.
     *
     * <p>이미 다른 여행에 붙어 있던 것도 이 여행으로 옮겨 온다 — 「고쳐 붙이기」를 위한 동작을
     * 따로 두지 않는다. 잘못 붙였을 때 사용자가 하는 일은 다시 고르는 것이지, 먼저 떼고 다시
     * 붙이는 두 단계가 아니다.
     *
     * <p><b>이체를 막지 않는다.</b> 카드 대금 납부가 여행 경비로 새는 것은 합계 규칙이 막고
     * 있고(이체는 지출에 잡히지 않는다), 여기서 거절하면 사용자가 일부러 붙여 둔 것까지
     * 되돌려야 한다. 화면이 기본 미선택으로 두고 이유를 적는다(R-15).
     */
    @Transactional
    public ExpenseAttachResponse attach(Long memberId, Long tripId, List<Long> transactionIds) {
        Trip trip = requireTrip(memberId, tripId);
        List<LedgerTransaction> targets = ownedTransactions(memberId, transactionIds);
        targets.forEach(tx -> tx.attachToTrip(trip.getId()));
        return new ExpenseAttachResponse(targets.size());
    }

    /**
     * 이 여행에서 뗀다. <b>거래는 지우지 않는다</b> — 연결만 끊긴다.
     *
     * <p>다른 여행에 붙어 있는 건은 건드리지 않는다. 「일본 가을에서 빼기」를 눌렀는데 오사카
     * 봄 여행의 연결까지 끊기면, 화면이 말한 것과 일어난 일이 다르다.
     */
    @Transactional
    public ExpenseAttachResponse detach(Long memberId, Long tripId, List<Long> transactionIds) {
        Trip trip = requireTrip(memberId, tripId);
        List<LedgerTransaction> targets = ownedTransactions(memberId, transactionIds).stream()
                .filter(tx -> trip.getId().equals(tx.getTripId()))
                .toList();
        targets.forEach(tx -> tx.attachToTrip(null));
        return new ExpenseAttachResponse(targets.size());
    }

    /** 남의 여행도 404 — 403이면 「그 id의 여행은 있다」가 새어나간다. */
    private Trip requireTrip(Long memberId, Long tripId) {
        return tripRepository.findByIdAndMemberId(tripId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_TRIP_NOT_FOUND));
    }

    /**
     * 내 거래만. 남의 것과 이미 지운 것은 <b>조용히 빠진다</b> — 수십 건을 한 번에 보내는
     * 동작이라, 한 건이 어긋났다고 전부 거절하면 사용자는 무엇이 문제인지 알 수 없다.
     * 그래서 응답이 「몇 건이 붙었는지」를 돌려준다.
     */
    private List<LedgerTransaction> ownedTransactions(Long memberId, List<Long> ids) {
        return transactionRepository.findAllByMemberIdAndIdInAndDeletedAtIsNull(memberId, ids);
    }
}
