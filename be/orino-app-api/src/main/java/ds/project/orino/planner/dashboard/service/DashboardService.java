package ds.project.orino.planner.dashboard.service;

import ds.project.orino.domain.calendar.entity.BlockStatus;
import ds.project.orino.domain.calendar.entity.BlockType;
import ds.project.orino.domain.calendar.entity.DailySchedule;
import ds.project.orino.domain.calendar.entity.ScheduleBlock;
import ds.project.orino.domain.calendar.repository.DailyScheduleRepository;
import ds.project.orino.domain.calendar.repository.ScheduleBlockRepository;
import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.goal.entity.Goal;
import ds.project.orino.domain.goal.entity.Milestone;
import ds.project.orino.domain.goal.entity.MilestoneStatus;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.repository.StudyUnitRepository;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.domain.review.entity.ReviewSchedule;
import ds.project.orino.domain.review.repository.ReviewScheduleRepository;
import ds.project.orino.domain.streak.entity.Streak;
import ds.project.orino.domain.streak.entity.StreakType;
import ds.project.orino.domain.streak.repository.StreakRepository;
import ds.project.orino.planner.dashboard.dto.DashboardHeatmapResponse;
import ds.project.orino.planner.dashboard.dto.DashboardStatisticsResponse;
import ds.project.orino.planner.dashboard.dto.DashboardStreaksResponse;
import ds.project.orino.planner.dashboard.dto.DashboardSummaryResponse;
import ds.project.orino.planner.dashboard.dto.StatisticsPeriod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final String DEFAULT_PACE_STATUS = "ON_TRACK";

    private final GoalRepository goalRepository;
    private final DailyScheduleRepository dailyScheduleRepository;
    private final ScheduleBlockRepository scheduleBlockRepository;
    private final StreakRepository streakRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final StudyUnitRepository studyUnitRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;

    public DashboardService(
            GoalRepository goalRepository,
            DailyScheduleRepository dailyScheduleRepository,
            ScheduleBlockRepository scheduleBlockRepository,
            StreakRepository streakRepository,
            UserPreferenceRepository userPreferenceRepository,
            StudyUnitRepository studyUnitRepository,
            ReviewScheduleRepository reviewScheduleRepository) {
        this.goalRepository = goalRepository;
        this.dailyScheduleRepository = dailyScheduleRepository;
        this.scheduleBlockRepository = scheduleBlockRepository;
        this.streakRepository = streakRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.studyUnitRepository = studyUnitRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
    }

    public DashboardSummaryResponse getSummary(Long memberId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        List<DashboardSummaryResponse.GoalSummary> goals = buildGoalSummaries(memberId);
        DashboardSummaryResponse.ThisWeek thisWeek = buildThisWeek(
                memberId, weekStart, weekEnd);
        DashboardSummaryResponse.Streaks streaks = buildSummaryStreaks(memberId);
        DashboardSummaryResponse.TodayProgress todayProgress =
                buildTodayProgress(memberId, today);

        return new DashboardSummaryResponse(
                goals, thisWeek, streaks, todayProgress);
    }

    public DashboardStreaksResponse getStreaks(Long memberId) {
        Streak overallStreak = streakRepository
                .findByMemberIdAndStreakTypeAndRoutineIsNull(
                        memberId, StreakType.OVERALL)
                .orElse(null);

        List<Streak> routineStreaks = streakRepository
                .findByMemberIdAndStreakType(memberId, StreakType.ROUTINE);

        int freezeUsed = overallStreak != null
                ? overallStreak.getFreezeUsedThisMonth() : 0;
        int freezeTotal = userPreferenceRepository.findByMemberId(memberId)
                .map(UserPreference::getStreakFreezePerMonth)
                .orElse(0);
        int freezeRemaining = Math.max(0, freezeTotal - freezeUsed);

        List<DashboardStreaksResponse.RoutineStreak> routines = routineStreaks.stream()
                .filter(s -> s.getRoutine() != null)
                .map(s -> new DashboardStreaksResponse.RoutineStreak(
                        s.getRoutine().getId(),
                        s.getRoutine().getTitle(),
                        s.getCurrentCount(),
                        s.getLongestCount()))
                .toList();

        DashboardStreaksResponse.Overall overall = overallStreak != null
                ? new DashboardStreaksResponse.Overall(
                        overallStreak.getCurrentCount(),
                        overallStreak.getLongestCount())
                : new DashboardStreaksResponse.Overall(0, 0);

        return new DashboardStreaksResponse(overall, routines, freezeRemaining);
    }

    public DashboardHeatmapResponse getHeatmap(Long memberId, Integer year) {
        int targetYear = year != null ? year : LocalDate.now().getYear();
        LocalDate start = LocalDate.of(targetYear, 1, 1);
        LocalDate end = LocalDate.of(targetYear, 12, 31);

        List<DailySchedule> schedules = dailyScheduleRepository
                .findByMemberIdAndScheduleDateBetween(memberId, start, end);

        List<DashboardHeatmapResponse.DayAchievement> days = schedules.stream()
                .sorted(Comparator.comparing(DailySchedule::getScheduleDate))
                .map(s -> new DashboardHeatmapResponse.DayAchievement(
                        s.getScheduleDate(),
                        computeAchievementRate(
                                s.getTotalBlocks(), s.getCompletedBlocks())))
                .toList();

        return new DashboardHeatmapResponse(targetYear, days);
    }

    public DashboardStatisticsResponse getStatistics(
            Long memberId, StatisticsPeriod period,
            LocalDate startDate, LocalDate endDate) {
        StatisticsPeriod resolvedPeriod = period != null
                ? period : StatisticsPeriod.WEEKLY;
        LocalDate[] range = resolveDateRange(resolvedPeriod, startDate, endDate);
        LocalDate from = range[0];
        LocalDate to = range[1];

        List<ScheduleBlock> blocks = scheduleBlockRepository
                .findByMemberIdAndDateBetween(memberId, from, to);

        int totalStudyMinutes = sumCompletedMinutes(blocks, BlockType.STUDY);
        int totalReviewMinutes = sumCompletedMinutes(blocks, BlockType.REVIEW);
        int reviewCompletionRate = computeReviewCompletionRate(blocks);
        List<DashboardStatisticsResponse.CategoryBreakdown> breakdown =
                buildCategoryBreakdown(blocks);

        return new DashboardStatisticsResponse(
                resolvedPeriod, totalStudyMinutes, totalReviewMinutes,
                reviewCompletionRate, breakdown);
    }

    private List<DashboardSummaryResponse.GoalSummary> buildGoalSummaries(
            Long memberId) {
        List<Goal> goals = goalRepository
                .findByMemberIdOrderByCreatedAtDesc(memberId);
        List<DashboardSummaryResponse.GoalSummary> result = new ArrayList<>();
        for (Goal goal : goals) {
            List<Milestone> milestones = goal.getMilestones();
            int total = milestones.size();
            int completed = (int) milestones.stream()
                    .filter(m -> m.getStatus() == MilestoneStatus.COMPLETED)
                    .count();
            int progressPercent = total > 0 ? completed * 100 / total : 0;
            result.add(new DashboardSummaryResponse.GoalSummary(
                    goal.getId(), goal.getTitle(),
                    progressPercent, DEFAULT_PACE_STATUS));
        }
        return result;
    }

    private DashboardSummaryResponse.ThisWeek buildThisWeek(
            Long memberId, LocalDate weekStart, LocalDate weekEnd) {
        List<ScheduleBlock> blocks = scheduleBlockRepository
                .findByMemberIdAndDateBetween(memberId, weekStart, weekEnd);
        int studyMinutes = blocks.stream()
                .filter(b -> b.getStatus() == BlockStatus.COMPLETED)
                .filter(b -> b.getBlockType() == BlockType.STUDY
                        || b.getBlockType() == BlockType.REVIEW)
                .mapToInt(DashboardService::blockDurationMinutes)
                .sum();
        int reviewRate = computeReviewCompletionRate(blocks);
        return new DashboardSummaryResponse.ThisWeek(studyMinutes, reviewRate);
    }

    private DashboardSummaryResponse.Streaks buildSummaryStreaks(Long memberId) {
        Streak overall = streakRepository
                .findByMemberIdAndStreakTypeAndRoutineIsNull(
                        memberId, StreakType.OVERALL)
                .orElse(null);
        DashboardSummaryResponse.OverallStreak overallDto = overall != null
                ? new DashboardSummaryResponse.OverallStreak(
                        overall.getCurrentCount(), overall.getLongestCount())
                : new DashboardSummaryResponse.OverallStreak(0, 0);

        List<DashboardSummaryResponse.RoutineStreak> routines = streakRepository
                .findByMemberIdAndStreakType(memberId, StreakType.ROUTINE)
                .stream()
                .filter(s -> s.getRoutine() != null)
                .map(s -> new DashboardSummaryResponse.RoutineStreak(
                        s.getRoutine().getId(),
                        s.getRoutine().getTitle(),
                        s.getCurrentCount()))
                .toList();

        return new DashboardSummaryResponse.Streaks(overallDto, routines);
    }

    private DashboardSummaryResponse.TodayProgress buildTodayProgress(
            Long memberId, LocalDate today) {
        return dailyScheduleRepository
                .findByMemberIdAndScheduleDate(memberId, today)
                .map(s -> new DashboardSummaryResponse.TodayProgress(
                        s.getTotalBlocks(), s.getCompletedBlocks()))
                .orElse(new DashboardSummaryResponse.TodayProgress(0, 0));
    }

    private LocalDate[] resolveDateRange(StatisticsPeriod period,
                                         LocalDate startDate,
                                         LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return new LocalDate[]{startDate, endDate};
        }
        LocalDate today = LocalDate.now();
        return switch (period) {
            case DAILY -> new LocalDate[]{today, today};
            case WEEKLY -> {
                LocalDate monday = today.with(
                        TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new LocalDate[]{monday, monday.plusDays(6)};
            }
            case MONTHLY -> {
                LocalDate first = today.withDayOfMonth(1);
                LocalDate last = today.with(
                        TemporalAdjusters.lastDayOfMonth());
                yield new LocalDate[]{first, last};
            }
        };
    }

    private int sumCompletedMinutes(List<ScheduleBlock> blocks,
                                    BlockType blockType) {
        return blocks.stream()
                .filter(b -> b.getBlockType() == blockType)
                .filter(b -> b.getStatus() == BlockStatus.COMPLETED)
                .mapToInt(DashboardService::blockDurationMinutes)
                .sum();
    }

    private int computeReviewCompletionRate(List<ScheduleBlock> blocks) {
        List<ScheduleBlock> reviews = blocks.stream()
                .filter(b -> b.getBlockType() == BlockType.REVIEW)
                .toList();
        if (reviews.isEmpty()) {
            return 0;
        }
        long completed = reviews.stream()
                .filter(b -> b.getStatus() == BlockStatus.COMPLETED)
                .count();
        return (int) (completed * 100 / reviews.size());
    }

    private List<DashboardStatisticsResponse.CategoryBreakdown>
            buildCategoryBreakdown(List<ScheduleBlock> blocks) {
        List<ScheduleBlock> studyReview = blocks.stream()
                .filter(b -> b.getStatus() == BlockStatus.COMPLETED)
                .filter(b -> b.getBlockType() == BlockType.STUDY
                        || b.getBlockType() == BlockType.REVIEW)
                .toList();
        if (studyReview.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> reviewIdToUnitId = loadReviewToUnitMap(studyReview);
        Map<Long, Category> unitIdToCategory = loadStudyUnitCategories(
                studyReview, reviewIdToUnitId);

        Map<Long, Integer> minutesByCategory = new LinkedHashMap<>();
        Map<Long, Category> categoryLookup = new HashMap<>();
        int totalMinutes = 0;
        for (ScheduleBlock block : studyReview) {
            Long unitId = resolveStudyUnitId(block, reviewIdToUnitId);
            if (unitId == null) {
                continue;
            }
            Category category = unitIdToCategory.get(unitId);
            if (category == null) {
                continue;
            }
            int minutes = blockDurationMinutes(block);
            totalMinutes += minutes;
            minutesByCategory.merge(category.getId(), minutes, Integer::sum);
            categoryLookup.putIfAbsent(category.getId(), category);
        }

        if (totalMinutes == 0) {
            return List.of();
        }

        int totalForPercent = totalMinutes;
        return minutesByCategory.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .map(e -> {
                    Category c = categoryLookup.get(e.getKey());
                    int minutes = e.getValue();
                    int percent = minutes * 100 / totalForPercent;
                    return new DashboardStatisticsResponse.CategoryBreakdown(
                            c.getId(), c.getName(), c.getColor(),
                            minutes, percent);
                })
                .collect(Collectors.toList());
    }

    private Map<Long, Long> loadReviewToUnitMap(
            List<ScheduleBlock> studyReviewBlocks) {
        Set<Long> reviewIds = studyReviewBlocks.stream()
                .filter(b -> b.getBlockType() == BlockType.REVIEW)
                .map(ScheduleBlock::getReferenceId)
                .collect(Collectors.toSet());
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (ReviewSchedule r : reviewScheduleRepository.findAllById(reviewIds)) {
            result.put(r.getId(), r.getStudyUnit().getId());
        }
        return result;
    }

    private Map<Long, Category> loadStudyUnitCategories(
            List<ScheduleBlock> studyReviewBlocks,
            Map<Long, Long> reviewIdToUnitId) {
        Set<Long> studyUnitIds = studyReviewBlocks.stream()
                .filter(b -> b.getBlockType() == BlockType.STUDY)
                .map(ScheduleBlock::getReferenceId)
                .collect(Collectors.toSet());
        studyUnitIds.addAll(reviewIdToUnitId.values());

        if (studyUnitIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Category> result = new HashMap<>();
        for (StudyUnit unit : studyUnitRepository.findAllById(studyUnitIds)) {
            Category category = unit.getMaterial().getCategory();
            if (category != null) {
                result.put(unit.getId(), category);
            }
        }
        return result;
    }

    private Long resolveStudyUnitId(ScheduleBlock block,
                                    Map<Long, Long> reviewIdToUnitId) {
        if (block.getBlockType() == BlockType.STUDY) {
            return block.getReferenceId();
        }
        if (block.getBlockType() == BlockType.REVIEW) {
            return reviewIdToUnitId.get(block.getReferenceId());
        }
        return null;
    }

    private static int computeAchievementRate(int total, int completed) {
        if (total == 0) {
            return 0;
        }
        return completed * 100 / total;
    }

    private static int blockDurationMinutes(ScheduleBlock block) {
        long minutes = Duration.between(
                block.getStartTime(), block.getEndTime()).toMinutes();
        if (minutes < 0) {
            minutes += 24 * 60;
        }
        return (int) minutes;
    }
}
