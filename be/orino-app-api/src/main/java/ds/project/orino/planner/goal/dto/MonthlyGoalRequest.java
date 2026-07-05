package ds.project.orino.planner.goal.dto;

/** 월간 목표 upsert 요청. content 검증은 서비스에서 수행한다(1~1000자·공백만 불가). */
public record MonthlyGoalRequest(String content) {
}
