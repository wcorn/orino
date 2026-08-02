/**
 * 학습일(study day) — 하루의 경계를 자정이 아니라 새벽 4시로 본다. BE의 `StudyDay`와 같은 규칙이다.
 *
 * 새벽 1시에 공부한 건 사용자에겐 "어젯밤 공부"다. 자정을 경계로 삼으면 그게 다음 날 몫이 되어
 * 복습 일정이 하루씩 밀렸다(#1003). 복습 due 시각이 원래부터 04:00 롤오버였으니,
 * "오늘/어제/내일"도 같은 경계를 쓴다.
 */

/** 하루가 바뀌는 시각. 이 시각부터 새 학습일이다. */
export const ROLLOVER_HOUR = 4;

/** 그 시각이 속한 학습일의 시작(= 그 학습일 04:00). 자정~04:00은 전날로 친다. */
export function studyDayStart(at: Date): Date {
  const shifted = new Date(at.getTime());
  shifted.setHours(shifted.getHours() - ROLLOVER_HOUR);
  return new Date(
    shifted.getFullYear(),
    shifted.getMonth(),
    shifted.getDate(),
    ROLLOVER_HOUR,
  );
}

/** 학습일 기준 일수 차(to − from). 같은 학습일 0, 다음 학습일 +1, 지난 학습일 −1. */
export function studyDayDiff(from: Date, to: Date): number {
  return Math.round(
    (studyDayStart(to).getTime() - studyDayStart(from).getTime()) / 86_400_000,
  );
}
