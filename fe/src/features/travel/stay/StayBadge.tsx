import { Hotel, Plus } from "lucide-react";

import type { StayBadgeItem } from "@/features/travel/lib/stayBadge";

interface StayBadgeProps {
  /** 보여줄 숙소. null이면 `＋ 숙소 추가`가 대신 선다. */
  item: StayBadgeItem | null;
  onOpen: (stayId: number) => void;
  onAdd: () => void;
  /** 오프라인이면 추가는 막고 조회는 남긴다(§4.6). */
  offline: boolean;
  /** 숙소가 없을 때 추가 버튼을 아예 그리지 않는다 — 리스트 아래 배지가 그렇다. */
  hideAdd?: boolean;
}

/**
 * 숙소 배지(§2.5) — 리스트 위아래에 같은 모양으로 선다.
 *
 * <p>무엇을 보여줄지는 여기서 정하지 않는다. 위는 체크아웃이 먼저이고 아래는 위와 다를
 * 때만 나오는데, 그 우선순위는 날짜를 아는 쪽의 판단이라 `lib/stayBadge.ts`가 갖는다.
 */
export function StayBadge({
  item,
  onOpen,
  onAdd,
  offline,
  hideAdd = false,
}: StayBadgeProps) {
  if (item === null) {
    if (hideAdd || offline) return null;
    return (
      <button
        type="button"
        onClick={onAdd}
        className="text-muted-foreground hover:bg-muted flex items-center gap-2 rounded-lg border border-dashed px-3 py-2 text-[13px]"
      >
        <Plus className="size-3.5 shrink-0" />
        숙소 추가
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={() => onOpen(item.stayId)}
      aria-label={`숙소 ${item.name}${item.note ? ` · ${item.note}` : ""}`}
      className="bg-muted hover:bg-accent flex items-center gap-2 rounded-lg px-3 py-2 text-left text-[13px]"
    >
      <Hotel className="size-3.5 shrink-0" />
      <span className="truncate font-medium">{item.name}</span>
      {item.note && (
        <span className="text-muted-foreground shrink-0">· {item.note}</span>
      )}
    </button>
  );
}
