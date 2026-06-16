import { addDays, startOfDay } from "@/features/review/calendar";

import type { PlannerEvent } from "./api/feed";

export const HOURS = Array.from({ length: 24 }, (_, i) => i);
const MINUTES_PER_DAY = 24 * 60;

/** cursor가 속한 일요일 시작 주의 7일. */
export function weekDays(cursor: Date): Date[] {
  const base = startOfDay(cursor);
  const sunday = addDays(base, -base.getDay());
  return Array.from({ length: 7 }, (_, i) => addDays(sunday, i));
}

/** "2026-06-10T14:30:00" → 자정 기준 분(870). */
function minutesOf(datetime: string): number {
  const hour = Number(datetime.slice(11, 13));
  const minute = Number(datetime.slice(14, 16));
  return hour * 60 + minute;
}

export interface PositionedEvent {
  event: PlannerEvent;
  /** 모두 0..1 비율: top/height는 하루 높이 대비, left/width는 컬럼 너비 대비. */
  top: number;
  height: number;
  left: number;
  width: number;
}

interface Block {
  event: PlannerEvent;
  startMin: number;
  endMin: number;
}

/**
 * 하루의 시간대 일정(종일 제외)을 겹침 분할 배치한다.
 * 겹치는 일정들은 한 클러스터로 묶어 컬럼을 나눠(left/width) 나란히 둔다.
 */
export function layoutDayEvents(events: PlannerEvent[]): PositionedEvent[] {
  const blocks: Block[] = events
    .filter((e) => !e.allDay && e.start.includes("T"))
    .map((e) => {
      const startMin = minutesOf(e.start);
      const endMin = e.end ? minutesOf(e.end) : startMin + 60;
      return {
        event: e,
        startMin,
        endMin: Math.min(Math.max(endMin, startMin + 30), MINUTES_PER_DAY),
      };
    })
    .sort((a, b) => a.startMin - b.startMin || a.endMin - b.endMin);

  const result: PositionedEvent[] = [];
  let cluster: { block: Block; col: number }[] = [];
  let columns: number[] = []; // 컬럼별 마지막 endMin
  let clusterEnd = -1;

  const flush = () => {
    const cols = columns.length || 1;
    for (const { block, col } of cluster) {
      result.push({
        event: block.event,
        top: block.startMin / MINUTES_PER_DAY,
        height: (block.endMin - block.startMin) / MINUTES_PER_DAY,
        left: col / cols,
        width: 1 / cols,
      });
    }
    cluster = [];
    columns = [];
    clusterEnd = -1;
  };

  for (const block of blocks) {
    if (cluster.length > 0 && block.startMin >= clusterEnd) {
      flush();
    }
    let col = columns.findIndex((end) => end <= block.startMin);
    if (col === -1) {
      col = columns.length;
      columns.push(block.endMin);
    } else {
      columns[col] = block.endMin;
    }
    cluster.push({ block, col });
    clusterEnd = Math.max(clusterEnd, block.endMin);
  }
  flush();

  return result;
}
