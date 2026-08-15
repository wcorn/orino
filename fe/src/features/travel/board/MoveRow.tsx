import { ChevronRight, Hotel, Link2, Plus } from "lucide-react";

import type { Move } from "@/features/travel/api/activities";
import { modeMeta, moveLabel } from "@/features/travel/lib/travelMode";

interface MoveRowProps {
  move: Move;
  /** 탭했을 때 — 이동 편집 시트를 연다. */
  onOpen: (move: Move) => void;
  /** 오프라인이면 캐시에서 온 값이고, 고쳐서 보낼 수 없다(§4.6). */
  offline: boolean;
}

/**
 * 일정 사이(또는 마지막 일정 → 숙소)의 이동 행(§S-04). 탭하면 편집 시트가 열린다.
 *
 * <p><b>아직 안 적은 구간도 행으로 그린다</b> — `+ 이동 추가`. 그 자리가 곧 입력 지점이라,
 * 값이 없다고 행을 없애면 어디를 눌러야 이동을 적는지 알 수 없다.
 *
 * <p>적어 둔 이름이 있으면 분류보다 이름을 앞세운다 — 현지에서 찾아야 하는 것은 `기차`가
 * 아니라 `나리타 익스프레스 3호`다.
 */
export function MoveRow({ move, onOpen, offline }: MoveRowProps) {
  const empty = move.mode === null;
  const toStay = move.toStayId !== null;
  const Icon = empty ? (toStay ? Hotel : Plus) : modeMeta(move.mode!).Icon;
  const label = toStay && empty ? "숙소로 이동 추가" : moveLabel(move);

  return (
    <li className={toStay ? "border-t pt-2.5" : undefined}>
      <button
        type="button"
        onClick={() => onOpen(move)}
        disabled={offline}
        aria-label={`이동 ${label}`}
        className={`ml-[52px] flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs ${
          // 적어 둔 이동은 계획의 일부라 또렷하게, 빈 자리는 안내라 흐리게 읽힌다.
          empty ? "text-muted-foreground/70" : "text-muted-foreground"
        } ${offline ? "opacity-60" : "hover:bg-muted"}`}
      >
        <Icon className="size-[13px] shrink-0" />
        {label}
        {/* 예매 링크가 붙어 있다는 표시. 현지에서 이 행이 티켓으로 가는 입구가 된다. */}
        {move.url && <Link2 className="size-3 shrink-0" />}
        <ChevronRight className="size-3 shrink-0" />
      </button>
    </li>
  );
}
