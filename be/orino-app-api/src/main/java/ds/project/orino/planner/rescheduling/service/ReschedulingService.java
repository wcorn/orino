package ds.project.orino.planner.rescheduling.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.calendar.entity.DailySchedule;
import ds.project.orino.domain.calendar.repository.DailyScheduleRepository;
import ds.project.orino.domain.material.entity.DeadlineMode;
import ds.project.orino.domain.material.entity.MaterialAllocation;
import ds.project.orino.domain.material.entity.MaterialStatus;
import ds.project.orino.domain.material.entity.StudyMaterial;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.entity.UnitStatus;
import ds.project.orino.domain.material.repository.MaterialAllocationRepository;
import ds.project.orino.domain.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.planner.rescheduling.dto.ApplyRescheduleResponse;
import ds.project.orino.planner.rescheduling.dto.RescheduleOption;
import ds.project.orino.planner.rescheduling.dto.RescheduleOptionsResponse;
import ds.project.orino.planner.rescheduling.dto.RescheduleStrategy;
import ds.project.orino.planner.scheduling.dirty.DirtyScheduleMarker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 연속 미완료 감지 + 리스케줄링 선택지 시뮬레이션/적용.
 *
 * missedDays: 오늘 이전부터 역순으로, totalBlocks>0 && completedBlocks<totalBlocks
 *   인 날들의 연속 구간 길이.
 * 대상 과목: deadline_mode=DEADLINE, status=ACTIVE, deadline>=today, PENDING 단위 존재.
 */
@Service
@Transactional(readOnly = true)
public class ReschedulingService {

    /** 연속 미완료 감지 최대 조회 범위(일). */
    private static final int MISSED_LOOKBACK_DAYS = 14;
    /** 과목 할당 fallback 시 최소값. */
    private static final int MIN_FALLBACK_MINUTES = 30;

    private final DailyScheduleRepository dailyScheduleRepository;
    private final StudyMaterialRepository materialRepository;
    private final MaterialAllocationRepository allocationRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final DirtyScheduleMarker dirtyScheduleMarker;

    public ReschedulingService(
            DailyScheduleRepository dailyScheduleRepository,
            StudyMaterialRepository materialRepository,
            MaterialAllocationRepository allocationRepository,
            UserPreferenceRepository preferenceRepository,
            DirtyScheduleMarker dirtyScheduleMarker) {
        this.dailyScheduleRepository = dailyScheduleRepository;
        this.materialRepository = materialRepository;
        this.allocationRepository = allocationRepository;
        this.preferenceRepository = preferenceRepository;
        this.dirtyScheduleMarker = dirtyScheduleMarker;
    }

    public RescheduleOptionsResponse getOptions(Long memberId) {
        LocalDate today = LocalDate.now();
        MissSummary miss = computeMissSummary(memberId, today);
        List<MaterialContext> contexts = collectMaterialContexts(
                memberId, today);
        UserPreference preference = loadPreference(memberId);
        int dailyCap = preference.getDailyStudyMinutes();

        List<RescheduleOption> options;
        if (contexts.isEmpty()) {
            options = List.of();
        } else {
            options = List.of(
                    buildPostponeOption(miss.missedDays(), contexts),
                    buildCompressOption(miss.missedDays(), contexts, dailyCap),
                    buildKeepDeadlineOption(contexts, dailyCap));
        }
        return new RescheduleOptionsResponse(
                miss.missedDays(), miss.missedItems(), options);
    }

    @Transactional
    public ApplyRescheduleResponse apply(
            Long memberId, RescheduleStrategy strategy) {
        if (strategy == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        LocalDate today = LocalDate.now();
        MissSummary miss = computeMissSummary(memberId, today);
        List<MaterialContext> contexts = collectMaterialContexts(
                memberId, today);
        if (contexts.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }
        UserPreference preference = loadPreference(memberId);
        int dailyCap = preference.getDailyStudyMinutes();

        switch (strategy) {
            case POSTPONE -> applyPostpone(contexts, miss.missedDays());
            case COMPRESS -> applyCompress(
                    contexts, miss.missedDays(), dailyCap);
            case KEEP_DEADLINE -> applyKeepDeadline(contexts, dailyCap);
            default -> throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        dirtyScheduleMarker.markDirtyFromToday(memberId);

        int affectedDays = computeAffectedDays(contexts, today);
        int newDailyStudyMinutes = contexts.stream()
                .mapToInt(MaterialContext::currentRate)
                .sum();
        return new ApplyRescheduleResponse(
                strategy, affectedDays, newDailyStudyMinutes);
    }

    private MissSummary computeMissSummary(Long memberId, LocalDate today) {
        LocalDate from = today.minusDays(MISSED_LOOKBACK_DAYS);
        LocalDate to = today.minusDays(1);
        if (to.isBefore(from)) {
            return new MissSummary(0, 0);
        }
        List<DailySchedule> past = dailyScheduleRepository
                .findByMemberIdAndScheduleDateBetween(memberId, from, to);
        java.util.Map<LocalDate, DailySchedule> byDate = new java.util.HashMap<>();
        for (DailySchedule s : past) {
            byDate.put(s.getScheduleDate(), s);
        }
        int streak = 0;
        int missedItems = 0;
        for (LocalDate d = to; !d.isBefore(from); d = d.minusDays(1)) {
            DailySchedule s = byDate.get(d);
            if (s == null || s.getTotalBlocks() == 0
                    || s.getCompletedBlocks() >= s.getTotalBlocks()) {
                break;
            }
            streak++;
            missedItems += s.getTotalBlocks() - s.getCompletedBlocks();
        }
        return new MissSummary(streak, missedItems);
    }

    private List<MaterialContext> collectMaterialContexts(
            Long memberId, LocalDate today) {
        List<StudyMaterial> materials = materialRepository
                .findByMemberIdOrderByCreatedAtDesc(memberId);
        List<MaterialContext> contexts = new ArrayList<>();
        int deadlineBoundCount = 0;
        int totalUserMinutes = loadPreference(memberId).getDailyStudyMinutes();
        for (StudyMaterial m : materials) {
            if (m.getStatus() != MaterialStatus.ACTIVE) {
                continue;
            }
            if (m.getDeadlineMode() != DeadlineMode.DEADLINE) {
                continue;
            }
            if (m.getDeadline() == null || m.getDeadline().isBefore(today)) {
                continue;
            }
            int remaining = m.getUnits().stream()
                    .filter(u -> u.getStatus() == UnitStatus.PENDING)
                    .mapToInt(StudyUnit::getEstimatedMinutes)
                    .sum();
            if (remaining <= 0) {
                continue;
            }
            deadlineBoundCount++;
            contexts.add(new MaterialContext(m, remaining, today));
        }
        if (deadlineBoundCount == 0) {
            return contexts;
        }
        int fallback = Math.max(MIN_FALLBACK_MINUTES,
                totalUserMinutes / deadlineBoundCount);
        for (MaterialContext ctx : contexts) {
            ctx.initializeRate(fallback);
        }
        return contexts;
    }

    private UserPreference loadPreference(Long memberId) {
        return preferenceRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
    }

    private RescheduleOption buildPostponeOption(
            int missedDays, List<MaterialContext> contexts) {
        LocalDate latest = null;
        boolean feasible = true;
        for (MaterialContext ctx : contexts) {
            int rate = Math.max(1, ctx.currentRate());
            int days = (int) Math.ceil(
                    (double) ctx.remainingMinutes() / rate);
            LocalDate estimate = ctx.today().plusDays(days);
            if (latest == null || estimate.isAfter(latest)) {
                latest = estimate;
            }
            if (estimate.isAfter(ctx.deadline())) {
                feasible = false;
            }
        }
        String description = "전체 일정 " + missedDays + "일 후퇴";
        return new RescheduleOption(
                RescheduleStrategy.POSTPONE, "뒤로 밀기", description,
                feasible, latest, null, null);
    }

    private RescheduleOption buildCompressOption(
            int missedDays, List<MaterialContext> contexts, int dailyCap) {
        int maxIncrease = 0;
        int totalNewRate = 0;
        boolean feasible = true;
        LocalDate latest = null;
        for (MaterialContext ctx : contexts) {
            int currentRate = Math.max(1, ctx.currentRate());
            long daysRemaining = Math.max(1,
                    ctx.today().until(ctx.deadline()).getDays());
            int missedMinutes = missedDays * currentRate;
            int extra = (int) Math.ceil(
                    (double) missedMinutes / daysRemaining);
            int newRate = currentRate + extra;
            int pct = Math.round((extra * 100f) / currentRate);
            totalNewRate += newRate;
            if (pct > maxIncrease) {
                maxIncrease = pct;
            }
            if (newRate > dailyCap) {
                feasible = false;
            }
            if (latest == null || ctx.deadline().isAfter(latest)) {
                latest = ctx.deadline();
            }
        }
        if (totalNewRate > dailyCap) {
            feasible = false;
        }
        return new RescheduleOption(
                RescheduleStrategy.COMPRESS, "압축",
                "남은 기간에 밀린 양 분산",
                feasible, latest, maxIncrease, null);
    }

    private RescheduleOption buildKeepDeadlineOption(
            List<MaterialContext> contexts, int dailyCap) {
        int maxIncrease = 0;
        int totalNewRate = 0;
        boolean feasible = true;
        LocalDate latest = null;
        for (MaterialContext ctx : contexts) {
            long daysRemaining = Math.max(1,
                    ctx.today().until(ctx.deadline()).getDays());
            int currentRate = Math.max(1, ctx.currentRate());
            int newRate = (int) Math.ceil(
                    (double) ctx.remainingMinutes() / daysRemaining);
            int extra = Math.max(0, newRate - currentRate);
            int pct = Math.round((extra * 100f) / currentRate);
            totalNewRate += newRate;
            if (pct > maxIncrease) {
                maxIncrease = pct;
            }
            if (newRate > dailyCap) {
                feasible = false;
            }
            if (latest == null || ctx.deadline().isAfter(latest)) {
                latest = ctx.deadline();
            }
        }
        if (totalNewRate > dailyCap) {
            feasible = false;
        }
        String warning = feasible ? null
                : "하루 가용시간 대비 학습량이 과다합니다";
        return new RescheduleOption(
                RescheduleStrategy.KEEP_DEADLINE, "데드라인 고정",
                "하루 학습량 증가로 기존 일정 유지",
                feasible, latest, maxIncrease, warning);
    }

    private void applyPostpone(
            List<MaterialContext> contexts, int missedDays) {
        for (MaterialContext ctx : contexts) {
            ctx.material().shiftDeadline(missedDays);
        }
    }

    private void applyCompress(
            List<MaterialContext> contexts, int missedDays, int dailyCap) {
        for (MaterialContext ctx : contexts) {
            int currentRate = Math.max(1, ctx.currentRate());
            long daysRemaining = Math.max(1,
                    ctx.today().until(ctx.deadline()).getDays());
            int missedMinutes = missedDays * currentRate;
            int extra = (int) Math.ceil(
                    (double) missedMinutes / daysRemaining);
            int newRate = Math.min(dailyCap, currentRate + extra);
            updateAllocation(ctx, newRate);
        }
    }

    private void applyKeepDeadline(
            List<MaterialContext> contexts, int dailyCap) {
        for (MaterialContext ctx : contexts) {
            long daysRemaining = Math.max(1,
                    ctx.today().until(ctx.deadline()).getDays());
            int newRate = (int) Math.ceil(
                    (double) ctx.remainingMinutes() / daysRemaining);
            int capped = Math.min(dailyCap, newRate);
            updateAllocation(ctx, capped);
        }
    }

    private void updateAllocation(MaterialContext ctx, int newRate) {
        StudyMaterial material = ctx.material();
        MaterialAllocation allocation = allocationRepository
                .findByMaterialId(material.getId())
                .orElse(null);
        if (allocation == null) {
            allocation = new MaterialAllocation(material, newRate);
            allocationRepository.save(allocation);
        } else {
            allocation.update(newRate);
        }
        ctx.setCurrentRate(newRate);
    }

    private int computeAffectedDays(
            List<MaterialContext> contexts, LocalDate today) {
        LocalDate latest = null;
        for (MaterialContext ctx : contexts) {
            LocalDate deadline = ctx.material().getDeadline();
            if (deadline == null) {
                continue;
            }
            if (latest == null || deadline.isAfter(latest)) {
                latest = deadline;
            }
        }
        if (latest == null) {
            return 0;
        }
        return (int) today.until(latest).getDays();
    }

    private record MissSummary(int missedDays, int missedItems) {
    }

    private static final class MaterialContext {
        private final StudyMaterial material;
        private final int remainingMinutes;
        private final LocalDate today;
        private int currentRate;

        MaterialContext(StudyMaterial material, int remainingMinutes,
                        LocalDate today) {
            this.material = material;
            this.remainingMinutes = remainingMinutes;
            this.today = today;
        }

        void initializeRate(int fallback) {
            MaterialAllocation allocation = material.getAllocation();
            this.currentRate = allocation != null
                    ? allocation.getDefaultMinutes() : fallback;
        }

        void setCurrentRate(int newRate) {
            this.currentRate = newRate;
        }

        StudyMaterial material() {
            return material;
        }

        int remainingMinutes() {
            return remainingMinutes;
        }

        LocalDate today() {
            return today;
        }

        LocalDate deadline() {
            return material.getDeadline();
        }

        int currentRate() {
            return currentRate;
        }
    }
}
