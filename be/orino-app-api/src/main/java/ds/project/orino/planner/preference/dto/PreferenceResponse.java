package ds.project.orino.planner.preference.dto;

import ds.project.orino.domain.material.entity.MissedPolicy;
import ds.project.orino.domain.preference.entity.StudyTimePreference;
import ds.project.orino.domain.preference.entity.UserPreference;

import java.time.LocalTime;

public record PreferenceResponse(
        LocalTime wakeTime,
        LocalTime sleepTime,
        int dailyStudyMinutes,
        StudyTimePreference studyTimePreference,
        int focusMinutes,
        int breakMinutes,
        String restDays,
        boolean skipHolidays,
        String defaultReviewIntervals,
        MissedPolicy defaultMissedPolicy,
        int streakFreezePerMonth
) {

    public static PreferenceResponse from(UserPreference p) {
        return new PreferenceResponse(
                p.getWakeTime(),
                p.getSleepTime(),
                p.getDailyStudyMinutes(),
                p.getStudyTimePreference(),
                p.getFocusMinutes(),
                p.getBreakMinutes(),
                p.getRestDays(),
                p.isSkipHolidays(),
                p.getDefaultReviewIntervals(),
                p.getDefaultMissedPolicy(),
                p.getStreakFreezePerMonth()
        );
    }
}
