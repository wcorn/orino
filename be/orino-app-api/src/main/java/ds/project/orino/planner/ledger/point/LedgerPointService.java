package ds.project.orino.planner.ledger.point;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerPoint;
import ds.project.orino.domain.planner.ledger.repository.LedgerPointRepository;
import ds.project.orino.planner.ledger.common.LedgerClock;
import ds.project.orino.planner.ledger.point.dto.PointDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 포인트·마일리지(`LDG-006`).
 *
 * <p><b>원장과 조인되지 않는다.</b> 총자산·순자산·통계 어디에도 들어가지 않는다 — 포인트는
 * 쓸 수 있는 곳이 정해진 외상이지 돈이 아니고, 섞는 순간 「자산이 얼마인가」가 답할 수 없는
 * 질문이 된다. 이 서비스가 {@code LedgerBalances}를 모른다는 사실 자체가 그 경계다.
 *
 * <p>적어 두는 이유의 절반은 <b>소멸일</b>이다. D-day를 서버가 계산해 내린다 — 화면이 세면
 * 시계가 둘이 되고, 자정 언저리에 서로 다른 날짜를 말한다.
 */
@Service
public class LedgerPointService {

    /** 이 안쪽으로 들어오면 알린다. 한 달이면 쓸 계획을 세울 수 있다. */
    private static final int EXPIRING_SOON_DAYS = 30;

    private final LedgerPointRepository pointRepository;
    private final LedgerClock clock;

    public LedgerPointService(LedgerPointRepository pointRepository, LedgerClock clock) {
        this.pointRepository = pointRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PointDtos.View> list(Long memberId) {
        LocalDate today = clock.today();
        return pointRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId).stream()
                .map(point -> view(point, today))
                .toList();
    }

    @Transactional
    public PointDtos.View create(Long memberId, PointDtos.SaveRequest request) {
        LedgerPoint point = pointRepository.save(new LedgerPoint(
                memberId, request.name(), request.unit(),
                request.balance() == null ? 0 : request.balance(),
                request.expiresOn(), request.memo(),
                request.displayOrder() == null ? 0 : request.displayOrder()));
        return view(point, clock.today());
    }

    @Transactional
    public PointDtos.View update(Long memberId, Long id, PointDtos.UpdateRequest request) {
        LedgerPoint point = pointRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_POINT_NOT_FOUND));
        point.update(request.name(), request.unit(), request.balance(), request.expiresOn(),
                Boolean.TRUE.equals(request.clearExpiry()), request.memo(),
                request.displayOrder());
        return view(point, clock.today());
    }

    @Transactional
    public void delete(Long memberId, Long id) {
        LedgerPoint point = pointRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_POINT_NOT_FOUND));
        pointRepository.delete(point);
    }

    private PointDtos.View view(LedgerPoint point, LocalDate today) {
        Integer daysLeft = point.getExpiresOn() == null
                ? null
                : (int) ChronoUnit.DAYS.between(today, point.getExpiresOn());
        return new PointDtos.View(point.getId(), point.getName(), point.getUnit(),
                point.getBalance(), point.getExpiresOn(), daysLeft,
                // 이미 지난 것도 「곧」이 아니다 — 지난 것은 지난 것으로 읽혀야 한다.
                daysLeft != null && daysLeft >= 0 && daysLeft <= EXPIRING_SOON_DAYS,
                point.getMemo(), point.getDisplayOrder());
    }
}
