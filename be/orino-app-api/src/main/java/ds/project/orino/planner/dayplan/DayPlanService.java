package ds.project.orino.planner.dayplan;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.dayplan.entity.DayPlanBlock;
import ds.project.orino.domain.planner.dayplan.repository.DayPlanBlockRepository;
import ds.project.orino.planner.dayplan.dto.DayPlanBlockRequest;
import ds.project.orino.planner.dayplan.dto.DayPlanBlockResponse;
import ds.project.orino.planner.dayplan.dto.DayPlanRequest;
import ds.project.orino.planner.dayplan.dto.DayPlanResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주간 계획표 — 멤버당 단일 주간 템플릿의 시간 블록 조회 + 전량 교체. orino 내부 데이터(Google·반복·미러 없음).
 */
@Service
@Transactional(readOnly = true)
public class DayPlanService {

    private static final int MIN_DAY_OF_WEEK = 0; // 일
    private static final int MAX_DAY_OF_WEEK = 6; // 토

    private final DayPlanBlockRepository blockRepository;

    public DayPlanService(DayPlanBlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    public DayPlanResponse getWeeklyPlan(Long memberId) {
        List<DayPlanBlockResponse> blocks = blockRepository.findAllByMemberId(memberId).stream()
                .sorted(Comparator.comparingInt(DayPlanBlock::getDayOfWeek)
                        .thenComparing(DayPlanBlock::getStartTime))
                .map(DayPlanBlockResponse::of)
                .toList();
        return new DayPlanResponse(blocks);
    }

    /** 주간 템플릿 전량 교체. 기존 블록을 모두 지우고 요청 블록으로 대체한다(요일 내 시작시각 순 sort_order). */
    @Transactional
    public DayPlanResponse replace(Long memberId, DayPlanRequest request) {
        validate(request.blocks());

        blockRepository.deleteByMemberId(memberId);
        blockRepository.flush();
        blockRepository.saveAll(buildBlocks(memberId, request.blocks()));

        return getWeeklyPlan(memberId);
    }

    /** 요일 범위·시간 역전·라벨 공백 검증. 위반 시 400 PLN-ERR-002. 같은 요일 겹침은 허용(약한 검증). */
    private void validate(List<DayPlanBlockRequest> blocks) {
        for (DayPlanBlockRequest block : blocks) {
            boolean invalid = block.dayOfWeek() < MIN_DAY_OF_WEEK || block.dayOfWeek() > MAX_DAY_OF_WEEK
                    || block.label() == null || block.label().isBlank()
                    || !block.endTime().isAfter(block.startTime());
            if (invalid) {
                throw new CustomException(ErrorCode.ROUTINE_INVALID_RULE);
            }
        }
    }

    private List<DayPlanBlock> buildBlocks(Long memberId, List<DayPlanBlockRequest> requests) {
        List<DayPlanBlockRequest> sorted = requests.stream()
                .sorted(Comparator.comparingInt(DayPlanBlockRequest::dayOfWeek)
                        .thenComparing(DayPlanBlockRequest::startTime))
                .toList();

        Map<Integer, Integer> nextOrderByDay = new HashMap<>();
        List<DayPlanBlock> blocks = new ArrayList<>();
        for (DayPlanBlockRequest request : sorted) {
            int order = nextOrderByDay.getOrDefault(request.dayOfWeek(), 0);
            nextOrderByDay.put(request.dayOfWeek(), order + 1);
            blocks.add(new DayPlanBlock(
                    memberId, request.dayOfWeek(), request.startTime(), request.endTime(),
                    request.label().trim(), request.color(), order));
        }
        return blocks;
    }
}
