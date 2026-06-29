import type {
  WeeklyPlanBlock,
  WeeklyPlanBlockInput,
} from "../../api/weeklyPlan";

export const DAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"];
export const DAY_LABELS_LONG = [
  "일요일",
  "월요일",
  "화요일",
  "수요일",
  "목요일",
  "금요일",
  "토요일",
];

export const HOUR_PX = 32; // 컴팩트하게(0~24 더 한눈에)
export const DAY_MINUTES = 24 * 60;
export const MAX_MINUTE = DAY_MINUTES - 1; // 23:59

/** 클라이언트 편집용 블록. 저장 전(id=null)·저장 후(id) 모두 표현하며 React key로 {@link EditableBlock.key} 사용. */
export interface EditableBlock {
  key: string;
  id: number | null;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  label: string;
  color: string | null;
}

/** 겹침 분할 렌더용 좌표(left/width는 0..1 비율). */
export interface LaidOutBlock extends EditableBlock {
  left: number;
  width: number;
}

let keySeq = 0;

export function nextKey(): string {
  keySeq += 1;
  return `b-${keySeq}`;
}

/** "HH:mm" → 자정 기준 분(0..1439). */
export function timeToMinutes(time: string): number {
  const [h, m] = time.split(":").map(Number);
  return h * 60 + m;
}

/** 분 → "HH:mm"(0..1440로 클램프, 1440="24:00"). */
export function minutesToTime(minutes: number): string {
  const clamped = Math.max(0, Math.min(DAY_MINUTES, Math.round(minutes)));
  const h = Math.floor(clamped / 60);
  const m = clamped % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

/** 종료 ≤ 시작이면 역전(저장 불가). */
export function isReversed(startTime: string, endTime: string): boolean {
  return timeToMinutes(endTime) <= timeToMinutes(startTime);
}

export function topRatio(startTime: string): number {
  return timeToMinutes(startTime) / DAY_MINUTES;
}

export function heightRatio(startTime: string, endTime: string): number {
  return (timeToMinutes(endTime) - timeToMinutes(startTime)) / DAY_MINUTES;
}

/** 선택한 여러 요일 각각에 같은 시간/라벨/색의 블록을 만든다(+ 버튼 생성). */
export function createBlocks(
  days: number[],
  startTime: string,
  endTime: string,
  label: string,
  color: string | null,
): EditableBlock[] {
  return days.map((dayOfWeek) => ({
    key: nextKey(),
    id: null,
    dayOfWeek,
    startTime,
    endTime,
    label,
    color,
  }));
}

/** 서버 블록 → 편집 블록. */
export function toEditable(blocks: WeeklyPlanBlock[]): EditableBlock[] {
  return blocks.map((b) => ({
    key: nextKey(),
    id: b.id,
    dayOfWeek: b.dayOfWeek,
    startTime: b.startTime,
    endTime: b.endTime,
    label: b.label,
    color: b.color,
  }));
}

/** 편집 블록 → 저장 요청(전량 교체). */
export function toInput(blocks: EditableBlock[]): WeeklyPlanBlockInput[] {
  return blocks.map((b) => ({
    dayOfWeek: b.dayOfWeek,
    startTime: b.startTime,
    endTime: b.endTime,
    label: b.label.trim(),
    color: b.color,
  }));
}

/**
 * 같은 요일 블록들의 겹침을 클러스터 단위로 컬럼 분할한다.
 * 서로 겹치는 블록 그룹 안에서 left/width(0..1)를 나눠 나란히 렌더한다(맞닿음은 겹침 아님).
 */
export function layoutDay(blocks: EditableBlock[]): LaidOutBlock[] {
  const sorted = [...blocks].sort((a, b) => {
    const byStart = timeToMinutes(a.startTime) - timeToMinutes(b.startTime);
    return byStart !== 0
      ? byStart
      : timeToMinutes(a.endTime) - timeToMinutes(b.endTime);
  });

  const result: LaidOutBlock[] = [];
  let cluster: { block: EditableBlock; col: number }[] = [];
  let colEnds: number[] = [];
  let clusterEnd = -1;

  const flush = () => {
    const cols = colEnds.length || 1;
    for (const placed of cluster) {
      result.push({
        ...placed.block,
        left: placed.col / cols,
        width: 1 / cols,
      });
    }
    cluster = [];
    colEnds = [];
    clusterEnd = -1;
  };

  for (const block of sorted) {
    const start = timeToMinutes(block.startTime);
    const end = timeToMinutes(block.endTime);
    if (cluster.length > 0 && start >= clusterEnd) {
      flush();
    }
    let col = colEnds.findIndex((colEnd) => colEnd <= start);
    if (col === -1) {
      col = colEnds.length;
      colEnds.push(end);
    } else {
      colEnds[col] = end;
    }
    cluster.push({ block, col });
    clusterEnd = Math.max(clusterEnd, end);
  }
  flush();
  return result;
}
