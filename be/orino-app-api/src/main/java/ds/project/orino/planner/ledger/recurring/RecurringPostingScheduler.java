package ds.project.orino.planner.ledger.recurring;

import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurring;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringOverride;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringStatus;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerRecurringOverrideRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerRecurringRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.common.LedgerBalances;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 예정일이 지난 회차를 <b>묻지 않고 적는다</b>(확정 명세 §6.3).
 *
 * <p>확인 대기도 승인 단계도 없다. 승인 버튼을 만들면 정기 항목을 만든 이유가 없어진다 —
 * 대신 「자동」 표시가 붙고 언제든 정정할 수 있다. <b>「자동」은 구분용이지 대기 상태가 아니다.</b>
 *
 * <p>앱은 돈을 옮기지 않는다. 실제 출금은 은행·카드사가 한다. "자동"은 <b>장부에 자동으로
 * 적힌다</b>는 뜻뿐이다.
 *
 * <p><b>중복은 DB가 막는다.</b> {@code DuplicateKey}를 잡아 넘기는 것은 예외 처리가 아니라
 * 설계다(D-2). 알림은 두 번 와도 사람이 알아채고 끝나지만, 원장 중복은 잔액·통계·청구서·
 * 예산이 전부 틀어지고 월말 대사에서야 발견된다 — 그때는 어느 게 중복인지 가려내기 어렵다.
 * {@code replicas: 1}에 기대지 않는다.
 *
 * <p><b>최초 등록 시 소급 생성은 하지 않는다.</b> 전개 하한은 {@code max(시작일, 등록일)}이다 —
 * 과거 시작일로 새 항목을 만들었다고 지난 6개월치가 쏟아지면 원장이 오염되고, 그걸 되돌리는
 * 건 사람 손이다. 반면 <b>등록 뒤 밀린 기간은 전부 따라잡는다</b>(서버가 며칠 꺼져 있었어도).
 */
@Component
public class RecurringPostingScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringPostingScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 영업일 보정이 회차를 <b>앞으로</b> 당길 수 있어, 예정일이 아직 안 왔어도 적힐 날은
     * 지났을 수 있다. {@code BusinessDays}의 최대 이동 폭만큼 앞을 내다본다.
     */
    private static final int LOOKAHEAD_DAYS = 14;

    private final LedgerRecurringRepository recurringRepository;
    private final LedgerRecurringOverrideRepository overrideRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerAssetRepository assetRepository;
    private final LedgerOccurrenceResolver resolver;
    private final LedgerRecurringPoster poster;
    private final java.time.Clock clock;

    public RecurringPostingScheduler(LedgerRecurringRepository recurringRepository,
                                     LedgerRecurringOverrideRepository overrideRepository,
                                     LedgerTransactionRepository transactionRepository,
                                     LedgerAssetRepository assetRepository,
                                     LedgerOccurrenceResolver resolver,
                                     LedgerRecurringPoster poster,
                                     java.time.Clock clock) {
        this.recurringRepository = recurringRepository;
        this.overrideRepository = overrideRepository;
        this.transactionRepository = transactionRepository;
        this.assetRepository = assetRepository;
        this.resolver = resolver;
        this.poster = poster;
        this.clock = clock;
    }

    /**
     * 매시 정각. 하루 한 번이면 새벽에 등록한 항목이 다음 날까지 안 적힌다.
     *
     * <p><b>트랜잭션을 걸지 않는다.</b> 회차마다 {@code REQUIRES_NEW}로 따로 커밋해야
     * 한 건이 튕겨도 나머지가 남는다.
     */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void postDue() {
        postDueOn(LocalDate.now(clock.withZone(ZONE)));
    }

    /** 「오늘」을 밖에서 주는 경로. 테스트가 이 문으로 들어온다. */
    public int postDueOn(LocalDate today) {
        List<LedgerRecurring> rules = recurringRepository.findAllByStatusIn(
                List.of(LedgerRecurringStatus.ACTIVE, LedgerRecurringStatus.PAUSED));
        if (rules.isEmpty()) {
            return 0;
        }
        Map<Long, Map<LocalDate, LedgerRecurringOverride>> overrides = overridesOf(rules);

        int posted = 0;
        Set<Long> touchedMembers = new HashSet<>();
        for (LedgerRecurring rule : rules) {
            int count = postRule(rule, overrides.getOrDefault(rule.getId(), Map.of()), today);
            if (count > 0) {
                posted += count;
                touchedMembers.add(rule.getMemberId());
            }
        }
        touchedMembers.forEach(this::warnIfNegative);
        if (posted > 0) {
            log.info("정기 항목 자동 기록: {}건", posted);
        }
        return posted;
    }

    private int postRule(LedgerRecurring rule,
                         Map<LocalDate, LedgerRecurringOverride> overrides,
                         LocalDate today) {
        LocalDate from = expandFrom(rule);
        if (from.isAfter(today.plusDays(LOOKAHEAD_DAYS))) {
            return 0;
        }
        int posted = 0;
        for (LedgerOccurrenceResolver.Occurrence occurrence :
                resolver.resolve(rule, overrides, from, today.plusDays(LOOKAHEAD_DAYS))) {
            // 건너뛰기·되돌리기·미납은 적지 않는다. 미납을 여기서 다시 적으면
            // 사람이 「안 빠졌다」고 표시한 것을 배치가 매시간 뒤집는다.
            if (occurrence.action() != null && occurrence.action().removesPosting()) {
                continue;
            }
            if (occurrence.date().isAfter(today)) {
                continue;
            }
            try {
                if (poster.post(rule, occurrence.occurrenceDate(), occurrence.date(),
                        occurrence.amount(), today) != null) {
                    posted++;
                }
            } catch (DataIntegrityViolationException alreadyPosted) {
                // 이미 적힌 회차다. 정상 경로다 — DB가 중복을 막았고, 우리는 넘어간다.
                continue;
            }
        }
        return posted;
    }

    /**
     * 전개 하한. 등록 시점에 박아 둔 {@code postingFrom}이 「최초 등록 시 소급 생성 안 함」의
     * 집행 지점이다.
     */
    private LocalDate expandFrom(LedgerRecurring rule) {
        LocalDate floor = rule.getPostingFrom();
        return floor.isAfter(rule.getStartDate()) ? floor : rule.getStartDate();
    }

    /**
     * 자동 기록으로 계좌가 음수가 되면 경고한다. <b>기록 자체는 막지 않는다</b>(§6.3).
     *
     * <p>막으면 「돈이 없어서 안 적힌 회차」가 생기고, 그건 실제로 빠져나간 돈을 장부에서
     * 지우는 것과 같다. 잔액이 음수라는 사실은 자산 화면이 이미 붉게 보여준다 —
     * 여기서 따로 저장하지 않는 이유이기도 하다.
     */
    private void warnIfNegative(Long memberId) {
        List<LedgerAsset> assets =
                assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId);
        LedgerBalances balances = LedgerBalances.of(assets,
                transactionRepository.sumConfirmedByAssetAndType(
                        memberId, LedgerTransactionStatus.CONFIRMED),
                transactionRepository.sumConfirmedByCounterAsset(
                        memberId, LedgerTransactionStatus.CONFIRMED));
        List<String> negative = new ArrayList<>();
        for (LedgerAsset asset : assets) {
            Long balance = balances.balanceOf(asset.getId());
            if (balance != null && balance < 0) {
                negative.add(asset.getName() + " " + balance);
            }
        }
        if (!negative.isEmpty()) {
            log.warn("정기 항목 자동 기록 후 잔액 음수 — memberId={} {}", memberId, negative);
        }
    }

    private Map<Long, Map<LocalDate, LedgerRecurringOverride>> overridesOf(
            List<LedgerRecurring> rules) {
        Map<Long, Map<LocalDate, LedgerRecurringOverride>> byRule = new HashMap<>();
        List<Long> ids = rules.stream().map(LedgerRecurring::getId).toList();
        for (LedgerRecurringOverride override : overrideRepository.findAllByRecurringIdIn(ids)) {
            byRule.computeIfAbsent(override.getRecurringId(), key -> new HashMap<>())
                    .put(override.getOccurrenceDate(), override);
        }
        return byRule;
    }
}
