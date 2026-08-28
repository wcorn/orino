package ds.project.orino.planner.ledger.common;

import ds.project.orino.domain.planner.ledger.entity.LedgerCategory;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;
import ds.project.orino.domain.planner.ledger.repository.LedgerCategoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerSettingsRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 가계부 최초 진입 시 필요한 것을 심는다 — 기본 카테고리 13종과 설정 1행.
 *
 * <p><b>회원 생성 훅이 아니다</b>(D-14). 기존 회원이 이미 있기 때문이다. 회원을 만들 때
 * 심는 구조로 짜면 이미 가입한 사람에게는 카테고리가 없고, 그들을 위한 소급 INSERT
 * changeset이 따로 필요해진다 — 그건 스키마가 아니라 데이터를 마이그레이션하는 일이다.
 *
 * <p>대신 가계부 API가 처음 불릴 때 한 번 심는다. 두 번째부터는 존재 확인 질의 하나로 끝난다.
 */
@Component
public class LedgerBootstrap {

    /**
     * 지출 기본 카테고리(확정 명세 §4.1).
     *
     * <p>「이자/수수료」가 들어 있는 것이 중요하다 — 카드 이자와 청구 수수료는 v1.5에서
     * <b>새 지출</b>로 기록되는데(이월 잔액은 아니다), 그때 갈 곳이 없으면 미분류로 쌓인다.
     */
    private static final List<String> EXPENSE_PRESET = List.of(
            "식비", "카페/간식", "교통", "주거/통신", "의료", "문화", "의류/미용",
            "경조사", "교육", "보험", "구독", "이자/수수료", "기타");

    /** 수입은 셋이면 충분하다. 더 잘게 나눠 봐야 채워지지 않는다. */
    private static final List<String> INCOME_PRESET = List.of("급여", "용돈", "기타");

    private final LedgerCategoryRepository categoryRepository;
    private final LedgerSettingsRepository settingsRepository;

    public LedgerBootstrap(LedgerCategoryRepository categoryRepository,
                           LedgerSettingsRepository settingsRepository) {
        this.categoryRepository = categoryRepository;
        this.settingsRepository = settingsRepository;
    }

    /** 가계부 API의 진입점마다 부른다. 이미 심었으면 아무 일도 하지 않는다. */
    @Transactional
    public void ensureSeeded(Long memberId) {
        ensureSettings(memberId);
        if (categoryRepository.existsByMemberId(memberId)) {
            return;
        }
        int order = 0;
        for (String name : EXPENSE_PRESET) {
            categoryRepository.save(
                    new LedgerCategory(memberId, LedgerFlow.EXPENSE, name, null, order++));
        }
        order = 0;
        for (String name : INCOME_PRESET) {
            categoryRepository.save(
                    new LedgerCategory(memberId, LedgerFlow.INCOME, name, null, order++));
        }
    }

    /** 설정은 카테고리와 별도로 본다 — 카테고리를 전부 지운 사람에게 설정까지 새로 만들지 않는다. */
    @Transactional
    public LedgerSettings ensureSettings(Long memberId) {
        return settingsRepository.findById(memberId)
                .orElseGet(() -> settingsRepository.save(new LedgerSettings(memberId)));
    }
}
