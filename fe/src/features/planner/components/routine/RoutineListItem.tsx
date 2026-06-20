import { CircleDashed, MoreVertical, Repeat } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Menu, MenuItem } from "@/components/ui/menu";

import type { RoutineSeriesSummary } from "../../api/routines";

interface Props {
  series: RoutineSeriesSummary;
  onEdit: (series: RoutineSeriesSummary) => void;
  onDelete: (series: RoutineSeriesSummary) => void;
}

/** 루틴 시리즈 한 행: 종류 아이콘 + 제목 + 반복 요약 + ⋯(수정/삭제). */
export function RoutineListItem({ series, onEdit, onDelete }: Props) {
  const Icon = series.type === "habit" ? CircleDashed : Repeat;

  return (
    <li className="flex items-center gap-3 rounded-lg border px-3 py-2.5">
      <Icon className="text-muted-foreground size-4 shrink-0" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{series.title}</p>
        <p className="text-muted-foreground text-xs">{series.recurrenceText}</p>
      </div>
      <Menu
        trigger={
          <Button
            size="icon-sm"
            variant="ghost"
            aria-label={`${series.title} 메뉴`}
          >
            <MoreVertical className="size-4" />
          </Button>
        }
      >
        <MenuItem onClick={() => onEdit(series)}>수정</MenuItem>
        <MenuItem variant="destructive" onClick={() => onDelete(series)}>
          삭제
        </MenuItem>
      </Menu>
    </li>
  );
}
