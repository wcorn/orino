package ds.project.orino.planner.ledger.card;

import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerStatement;
import ds.project.orino.domain.planner.ledger.repository.LedgerStatementRepository;
import ds.project.orino.planner.holiday.BusinessDays;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 카드 사용 건을 사이클에 편입한다 — <b>산식의 출발점</b>이다.
 *
 * <p>거래를 적는 순간 붙인다. 나중에 청구서를 열 때 날짜로 훑어 찾을 수도 있지만, 그러면 사이클
 * 설정을 바꿨을 때 과거 거래가 조용히 다른 청구서로 옮겨간다 — 이미 결제까지 끝난 청구서의
 * 금액이 나중에 바뀌면 그건 원장이 아니다.
 *
 * <p>거래 저장 쪽에서 쓰는 <b>얇은 협력자</b>다. 청구서 조회·결제 로직을 여기 두지 않는다 —
 * 그쪽이 거래 저장을 다시 부르면 순환이 된다.
 */
@Component
public class LedgerStatementAssigner {

    private final LedgerStatementRepository statementRepository;
    private final BusinessDays businessDays;

    public LedgerStatementAssigner(LedgerStatementRepository statementRepository,
                                   BusinessDays businessDays) {
        this.statementRepository = statementRepository;
        this.businessDays = businessDays;
    }

    /**
     * 그 날짜를 품는 청구서. 없으면 만든다.
     *
     * <p>사이클 설정이 없는 카드(또는 카드가 아닌 자산)면 비어 있다 — 편입할 곳이 없다는 뜻이고,
     * 그건 오류가 아니라 <b>아직 사이클을 등록하지 않았다</b>는 상태다. 카드를 만들자마자
     * 사이클을 강제하면 「일단 적어 두기」가 막힌다.
     */
    public Optional<LedgerStatement> resolveFor(LedgerAsset card, LocalDate date) {
        if (!card.hasBillingCycle()) {
            return Optional.empty();
        }
        LedgerBillingCycle.Cycle cycle = LedgerBillingCycle.covering(card, date);
        return Optional.of(findOrCreate(card, cycle));
    }

    /** 사이클 하나에 해당하는 청구서를 가져오거나 만든다. */
    public LedgerStatement findOrCreate(LedgerAsset card, LedgerBillingCycle.Cycle cycle) {
        return statementRepository
                .findByCardAssetIdAndCycleStart(card.getId(), cycle.start())
                .orElseGet(() -> statementRepository.save(new LedgerStatement(
                        card.getMemberId(), card.getId(), cycle.start(), cycle.end(),
                        // 결제일이 주말·공휴일이면 앞 영업일로 당긴다 — 카드사가 그렇게 한다.
                        businessDays.previousBusinessDayOrSame(cycle.paymentDate()))));
    }
}
