import { Car, ChevronRight, Footprints } from "lucide-react";

import type { Leg } from "@/features/travel/api/activities";
import { legLabel } from "@/features/travel/lib/legLabel";

interface LegRowProps {
  leg: Leg;
  onOpen: (leg: Leg) => void;
}

/** 일정 사이의 이동시간 행(§S-04). 탭하면 이동수단 시트가 열린다. */
export function LegRow({ leg, onOpen }: LegRowProps) {
  const Icon = leg.mode === "WALK" ? Footprints : Car;

  return (
    <li>
      <button
        type="button"
        onClick={() => onOpen(leg)}
        aria-label={`이동시간 ${legLabel(leg)}`}
        className="text-muted-foreground hover:bg-muted ml-[52px] flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs"
      >
        <Icon className="size-[13px] shrink-0" />
        {legLabel(leg)}
        <ChevronRight className="size-3 shrink-0" />
      </button>
    </li>
  );
}
