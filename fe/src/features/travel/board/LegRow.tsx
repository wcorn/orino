import { Car, ChevronRight, Footprints } from "lucide-react";

import type { Leg } from "@/features/travel/api/activities";
import { legLabel } from "@/features/travel/lib/legLabel";

interface LegRowProps {
  leg: Leg;
  onOpen: (leg: Leg) => void;
  /** 오프라인이면 캐시에서 온 값이고, 다른 수단은 물어볼 수 없다(§4.6). */
  offline: boolean;
}

/** 일정 사이의 이동시간 행(§S-04). 탭하면 이동수단 시트가 열린다. */
export function LegRow({ leg, onOpen, offline }: LegRowProps) {
  const Icon = leg.mode === "WALK" ? Footprints : Car;

  return (
    <li>
      <button
        type="button"
        onClick={() => onOpen(leg)}
        disabled={offline}
        aria-label={`이동시간 ${legLabel(leg)}`}
        className={`text-muted-foreground ml-[52px] flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs ${
          offline ? "opacity-60" : "hover:bg-muted"
        }`}
      >
        <Icon className="size-[13px] shrink-0" />
        {legLabel(leg)}
        <ChevronRight className="size-3 shrink-0" />
      </button>
    </li>
  );
}
