package ds.project.orino.planner.dayplan;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.dayplan.entity.DayPlan;
import ds.project.orino.domain.planner.dayplan.entity.DayPlanBlock;
import ds.project.orino.domain.planner.dayplan.repository.DayPlanRepository;
import ds.project.orino.planner.dayplan.dto.DayPlanBlockRequest;
import ds.project.orino.planner.dayplan.dto.DayPlanBlockResponse;
import ds.project.orino.planner.dayplan.dto.DayPlanListResponse;
import ds.project.orino.planner.dayplan.dto.DayPlanRequest;
import ds.project.orino.planner.dayplan.dto.DayPlanResponse;
import ds.project.orino.planner.dayplan.dto.PlanInstancesResponse;
import ds.project.orino.planner.google.recurrence.RecurrenceRule;
import ds.project.orino.planner.google.routine.RecurrenceTextFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 데일리 플랜 CRUD + 펼침. orino가 진실(Google 무관). 블록은 declarative 전량 교체(id 기준 upsert/삭제),
 * 반복 규칙은 {@link DayPlanRecurrenceMapper}로 컬럼 ↔ RecurrenceRule 매핑한다.
 */
@Service
@Transactional(readOnly = true)
public class DayPlanService {

    private final DayPlanRepository dayPlanRepository;
    private final DayPlanRecurrenceMapper recurrenceMapper;

    public DayPlanService(DayPlanRepository dayPlanRepository, DayPlanRecurrenceMapper recurrenceMapper) {
        this.dayPlanRepository = dayPlanRepository;
        this.recurrenceMapper = recurrenceMapper;
    }

    @Transactional
    public DayPlanResponse create(Long memberId, DayPlanRequest request) {
        RecurrenceRule rule = recurrenceMapper.toRule(request.recurrence());
        validateBlocks(request.blocks());

        DayPlan plan = new DayPlan(
                memberId, request.name(), request.color(),
                recurrenceMapper.freqColumn(rule), rule.effectiveInterval(),
                recurrenceMapper.byDayColumn(rule), recurrenceMapper.byMonthDayColumn(rule),
                request.recurrence().startsOn(), rule.until());
        applyBlocks(plan, request.blocks());

        dayPlanRepository.saveAndFlush(plan);
        return toResponse(plan);
    }

    @Transactional
    public DayPlanResponse update(Long memberId, Long planId, DayPlanRequest request) {
        DayPlan plan = getOwned(memberId, planId);
        RecurrenceRule rule = recurrenceMapper.toRule(request.recurrence());
        validateBlocks(request.blocks());

        plan.updateMeta(
                request.name(), request.color(),
                recurrenceMapper.freqColumn(rule), rule.effectiveInterval(),
                recurrenceMapper.byDayColumn(rule), recurrenceMapper.byMonthDayColumn(rule),
                request.recurrence().startsOn(), rule.until());
        applyBlocks(plan, request.blocks());

        dayPlanRepository.flush();
        return toResponse(plan);
    }

    @Transactional
    public void delete(Long memberId, Long planId) {
        dayPlanRepository.delete(getOwned(memberId, planId));
    }

    public DayPlanListResponse list(Long memberId) {
        List<DayPlanResponse> plans = dayPlanRepository.findAllByMemberIdOrderByIdAsc(memberId).stream()
                .map(this::toResponse)
                .toList();
        return new DayPlanListResponse(plans);
    }

    /** [from, to] 구간을 활성 플랜으로 펼쳐 날짜별 블록을 만든다(배경 레이어). 블록 없는 날짜는 생략. */
    public PlanInstancesResponse instances(Long memberId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        Map<LocalDate, List<PlanInstancesResponse.Block>> byDate = new TreeMap<>();
        for (DayPlan plan : dayPlanRepository.findAllByMemberIdAndEnabledTrue(memberId)) {
            RecurrenceRule rule = recurrenceMapper.toRule(plan);
            List<DayPlanBlock> blocks = sortedBlocks(plan);
            for (LocalDate date : PlanExpander.occurrences(rule, plan.getStartsOn(), from, to)) {
                List<PlanInstancesResponse.Block> dayBlocks =
                        byDate.computeIfAbsent(date, d -> new ArrayList<>());
                for (DayPlanBlock block : blocks) {
                    dayBlocks.add(new PlanInstancesResponse.Block(
                            plan.getId(), plan.getName(), plan.getColor(),
                            block.getStartTime(), block.getEndTime(), block.getLabel()));
                }
            }
        }
        List<PlanInstancesResponse.Day> days = byDate.entrySet().stream()
                .map(e -> new PlanInstancesResponse.Day(e.getKey(), e.getValue()))
                .toList();
        return new PlanInstancesResponse(days);
    }

    private DayPlan getOwned(Long memberId, Long planId) {
        return dayPlanRepository.findByIdAndMemberId(planId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 시간 역전(end<=start)·범위 블록 간 겹침을 검증한다. 위반 시 PLN-ERR-002. */
    private void validateBlocks(List<DayPlanBlockRequest> blocks) {
        for (DayPlanBlockRequest block : blocks) {
            if (block.endTime() != null && !block.endTime().isAfter(block.startTime())) {
                throw new CustomException(ErrorCode.ROUTINE_INVALID_RULE);
            }
        }
        List<DayPlanBlockRequest> ranges = blocks.stream()
                .filter(b -> b.endTime() != null)
                .sorted(Comparator.comparing(DayPlanBlockRequest::startTime))
                .toList();
        for (int i = 1; i < ranges.size(); i++) {
            if (ranges.get(i).startTime().isBefore(ranges.get(i - 1).endTime())) {
                throw new CustomException(ErrorCode.ROUTINE_INVALID_RULE);
            }
        }
    }

    /** declarative 교체: 시작시각 순으로 sort_order를 재부여하고, id 기준 수정/신규/삭제한다. */
    private void applyBlocks(DayPlan plan, List<DayPlanBlockRequest> requests) {
        Map<Long, DayPlanBlock> existing = plan.getBlocks().stream()
                .filter(block -> block.getId() != null)
                .collect(Collectors.toMap(DayPlanBlock::getId, Function.identity()));
        List<DayPlanBlockRequest> sorted = requests.stream()
                .sorted(Comparator.comparing(DayPlanBlockRequest::startTime))
                .toList();

        Set<Long> kept = new HashSet<>();
        int order = 0;
        for (DayPlanBlockRequest request : sorted) {
            if (request.id() != null && existing.containsKey(request.id())) {
                existing.get(request.id()).update(
                        request.startTime(), request.endTime(), request.label(), request.chime(), order);
                kept.add(request.id());
            } else {
                plan.addBlock(new DayPlanBlock(
                        plan, request.startTime(), request.endTime(),
                        request.label(), request.chime(), order));
            }
            order++;
        }
        plan.getBlocks().removeIf(block -> block.getId() != null && !kept.contains(block.getId()));
    }

    private DayPlanResponse toResponse(DayPlan plan) {
        RecurrenceRule rule = recurrenceMapper.toRule(plan);
        List<DayPlanBlockResponse> blocks = sortedBlocks(plan).stream()
                .map(DayPlanBlockResponse::of)
                .toList();
        return new DayPlanResponse(
                plan.getId(), plan.getName(), plan.getColor(), plan.isEnabled(),
                recurrenceMapper.toDto(rule, plan.getStartsOn()),
                RecurrenceTextFormatter.toKorean(rule),
                blocks);
    }

    private List<DayPlanBlock> sortedBlocks(DayPlan plan) {
        return plan.getBlocks().stream()
                .sorted(Comparator.comparingInt(DayPlanBlock::getSortOrder))
                .toList();
    }
}
