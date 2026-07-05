package ds.project.orino.planner.goal.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.goal.entity.MonthlyGoal;
import ds.project.orino.domain.planner.goal.repository.MonthlyGoalRepository;
import ds.project.orino.planner.goal.dto.MonthlyGoalRequest;
import ds.project.orino.planner.goal.dto.MonthlyGoalResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MonthlyGoalService {

    private static final int MAX_CONTENT_LENGTH = 1000;

    private final MonthlyGoalRepository repository;

    public MonthlyGoalService(MonthlyGoalRepository repository) {
        this.repository = repository;
    }

    /** 조회. 없으면 null(응답 data:null). */
    public MonthlyGoalResponse find(Long memberId, int year, int month) {
        return repository.findByMemberIdAndYearAndMonth(memberId, year, month)
                .map(MonthlyGoalResponse::of)
                .orElse(null);
    }

    /** upsert. 있으면 content 갱신, 없으면 생성. */
    @Transactional
    public MonthlyGoalResponse upsert(Long memberId, int year, int month,
                                      MonthlyGoalRequest request) {
        validateMonth(month);
        String content = request.content();
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        MonthlyGoal goal = repository.findByMemberIdAndYearAndMonth(memberId, year, month)
                .orElse(null);
        if (goal == null) {
            goal = repository.save(new MonthlyGoal(memberId, year, month, content));
        } else {
            goal.updateContent(content);
        }
        return MonthlyGoalResponse.of(goal);
    }

    /** 삭제. 없어도 성공(idempotent). */
    @Transactional
    public void delete(Long memberId, int year, int month) {
        repository.findByMemberIdAndYearAndMonth(memberId, year, month)
                .ifPresent(repository::delete);
    }

    private void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }
}
