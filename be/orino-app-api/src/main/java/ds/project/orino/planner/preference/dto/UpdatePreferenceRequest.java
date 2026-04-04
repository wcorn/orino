package ds.project.orino.planner.preference.dto;

import ds.project.orino.domain.material.entity.MissedPolicy;
import ds.project.orino.domain.preference.entity.StudyTimePreference;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record UpdatePreferenceRequest(
        @NotNull LocalTime wakeTime,
        @NotNull LocalTime sleepTime,
        @Positive int dailyStudyMinutes,
        @NotNull StudyTimePreference studyTimePreference,
        @Positive int focusMinutes,
        @Positive int breakMinutes,
        @Size(max = 30) String restDays,
        Boolean skipHolidays,
        @NotNull @Size(max = 50) String defaultReviewIntervals,
        @NotNull MissedPolicy defaultMissedPolicy,
        int streakFreezePerMonth
) {
}
