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
                        .thenComparingInt(DayPlanBlock::getStartMinute))
                .map(DayPlanBlockResponse::of)
                .toList();
        return new DayPlanResponse(blocks);
    }

    /** 주간 템플릿 전량 교체. 기존 블록을 모두 지우고 요청 블록으로 대체한다(요일 내 시작시각 순 sort_order). */
    @Transactional
    public DayPlanResponse replace(Long memberId, DayPlanRequest request) {
        List<ParsedBlock> parsed = request.blocks().stream()
                .map(this::parseAndValidate)
                .toList();

        blockRepository.deleteByMemberId(memberId);
        blockRepository.flush();
        blockRepository.saveAll(buildBlocks(memberId, parsed));

        return getWeeklyPlan(memberId);
    }

    /** 검증 통과한 블록(분 단위). */
    private record ParsedBlock(int dayOfWeek, int startMinute, int endMinute, String label, String color) {
    }

    /**
     * 시각 파싱 + 요일 범위·시간 역전·라벨 공백 검증. 위반 시 400 PLN-ERR-002.
     * start 0~1439, end 1~1440(1440=자정), end&gt;start. 같은 요일 겹침은 허용(약한 검증).
     */
    private ParsedBlock parseAndValidate(DayPlanBlockRequest block) {
        int startMinute;
        int endMinute;
        try {
            startMinute = WeeklyPlanTime.toMinutes(block.startTime());
            endMinute = WeeklyPlanTime.toMinutes(block.endTime());
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.ROUTINE_INVALID_RULE);
        }

        boolean invalid = block.dayOfWeek() < MIN_DAY_OF_WEEK || block.dayOfWeek() > MAX_DAY_OF_WEEK
                || block.label() == null || block.label().isBlank()
                || startMinute < 0 || startMinute >= WeeklyPlanTime.DAY_MINUTES
                || endMinute < 1 || endMinute > WeeklyPlanTime.DAY_MINUTES
                || endMinute <= startMinute;
        if (invalid) {
            throw new CustomException(ErrorCode.ROUTINE_INVALID_RULE);
        }
        return new ParsedBlock(
                block.dayOfWeek(), startMinute, endMinute, block.label().trim(), block.color());
    }

    private List<DayPlanBlock> buildBlocks(Long memberId, List<ParsedBlock> parsed) {
        List<ParsedBlock> sorted = parsed.stream()
                .sorted(Comparator.comparingInt(ParsedBlock::dayOfWeek)
                        .thenComparingInt(ParsedBlock::startMinute))
                .toList();

        Map<Integer, Integer> nextOrderByDay = new HashMap<>();
        List<DayPlanBlock> blocks = new ArrayList<>();
        for (ParsedBlock block : sorted) {
            int order = nextOrderByDay.getOrDefault(block.dayOfWeek(), 0);
            nextOrderByDay.put(block.dayOfWeek(), order + 1);
            blocks.add(new DayPlanBlock(
                    memberId, block.dayOfWeek(), block.startMinute(), block.endMinute(),
                    block.label(), block.color(), order));
        }
        return blocks;
    }
}
