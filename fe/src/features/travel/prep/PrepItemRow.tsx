import { Link, TriangleAlert, X } from "lucide-react";

import { Checkbox } from "@/components/ui/checkbox";
import { cn } from "@/lib/utils";

import type { PrepItemView } from "../api/prep";

interface PrepItemRowProps {
  item: PrepItemView;
  /** 오프라인이면 체크·삭제를 막는다. 보는 것은 그대로 된다(§13). */
  offline: boolean;
  onToggle: (item: PrepItemView, done: boolean) => void;
  onOpen: (item: PrepItemView) => void;
  onDelete: (item: PrepItemView) => void;
}

/**
 * 준비 항목 한 줄. <b>이 화면에서 새로 만드는 유일한 컴포넌트다</b>(§10.0).
 *
 * <p><b>체크해도 자리를 옮기지 않는다.</b> 취소선과 흐리게만 걸린다 — 짐 싸기는 목록을
 * 위에서 아래로 훑는 작업이라, 방금 누른 줄이 눈앞에서 사라지면 어디까지 했는지 잃는다.
 * 다 끝나고 보기 싫으면 「완료 숨기기」를 켠다(§13).
 *
 * <p>기한 지남에 「무시」를 두지 않는다. 체크하거나 기한을 옮겨야 사라진다 — 끌 수 있는
 * 경고는 곧 아무도 안 보는 경고가 된다.
 */
export function PrepItemRow({
  item,
  offline,
  onToggle,
  onOpen,
  onDelete,
}: PrepItemRowProps) {
  return (
    <li className="flex min-h-11 items-center gap-2.5 px-4 py-2.5">
      <Checkbox
        checked={item.done}
        disabled={offline}
        aria-label={item.title}
        onChange={(event) => onToggle(item, event.currentTarget.checked)}
      />

      {/*
        제목을 누르면 편집 시트가 열린다. 행 전체를 누르게 하면 체크박스를 노리다 빗나간
        손가락이 매번 시트를 연다 — 이 화면에서 가장 자주 하는 동작이 체크다.
      */}
      <button
        type="button"
        onClick={() => onOpen(item)}
        className={cn(
          "flex-1 truncate text-left text-sm",
          item.done && "text-muted-foreground line-through",
        )}
      >
        {item.title}
      </button>

      {item.quantity !== null && (
        <span className="text-muted-foreground text-xs tabular-nums">
          {item.quantity}
        </span>
      )}

      {item.dueDaysBefore !== null && (
        <span
          className={cn(
            "ml-auto flex items-center gap-1 text-xs tabular-nums",
            item.overdue
              ? "text-destructive font-semibold"
              : "text-muted-foreground",
          )}
          title={item.dueDate ?? undefined}
        >
          {item.overdue && <TriangleAlert className="size-3.5 shrink-0" />}
          D-{item.dueDaysBefore}
        </span>
      )}

      {item.url && (
        <a
          href={item.url}
          target="_blank"
          rel="noreferrer noopener"
          aria-label={`${item.title} 링크 열기`}
          className="text-muted-foreground hover:text-foreground shrink-0"
        >
          <Link className="size-3.5" />
        </a>
      )}

      {/*
        삭제는 데스크톱에만 둔다(§10.1). 좁은 화면에서 체크박스 옆에 X를 붙이면 누르려던
        것과 지우는 것이 손가락 하나 차이가 된다 — 모바일은 시트 안에서 지운다.
      */}
      <button
        type="button"
        disabled={offline}
        aria-label={`${item.title} 삭제`}
        onClick={() => onDelete(item)}
        className="text-muted-foreground hover:text-destructive hidden shrink-0 disabled:opacity-40 sm:block"
      >
        <X className="size-3.5" />
      </button>
    </li>
  );
}
