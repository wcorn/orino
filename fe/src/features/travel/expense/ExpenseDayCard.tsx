import { ChevronDown, ChevronRight } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

import {
  EXPENSE_CATEGORY_LABELS,
  type ExpenseGroup,
  type ExpenseRow,
} from "../api/expenses";
import { formatAmount } from "../lib/money";

interface ExpenseDayCardProps {
  group: ExpenseGroup;
  open: boolean;
  onToggleOpen: () => void;
  /** 오늘 묶음이면 헤더에 배지를 단다. 기본 펼침도 이 묶음 하나뿐이다(§10.2). */
  today: boolean;
  /** 행을 누르면 여행 안에서 편집 시트가 열린다 — 가계부로 나가지 않는다(D-43). */
  onEdit: (row: ExpenseRow) => void;
  /** 지난 예정의 「확정」. 승격 배치가 없으므로 이 길이 유일하다(§4.3). */
  onConfirm: (row: ExpenseRow) => void;
  /** 오프라인이면 쓰기 입구를 잠근다 — 큐에 쌓아 나중에 보내지 않는다(D-33). */
  offline: boolean;
}

/**
 * 날짜 묶음 카드(화면 §10.2). <b>준비의 분류 카드와 같은 껍데기·같은 헤더 버튼</b>이다 —
 * 두 화면이 같은 여행 안에서 나란히 쓰이므로 눌러야 열린다는 사실이 같아야 한다.
 *
 * <p><b>행은 링크가 아니라 버튼이다.</b> 예전에는 가계부 지출 상세로 나가는 링크였다 —
 * 편집 화면이 가계부에 있었기 때문이다(D-35). 그 화면이 사라지면서 편집이 여행 안으로
 * 들어왔고, 「같은 화면이 둘」이라는 걱정도 함께 사라졌다.
 */
export function ExpenseDayCard({
  group,
  open,
  onToggleOpen,
  today,
  onEdit,
  onConfirm,
  offline,
}: ExpenseDayCardProps) {
  return (
    <section className="bg-card ring-foreground/10 rounded-xl ring-1">
      {/* 이름을 「2일차 · 오사카 4.3만」으로 못박는다 — 준비 카드와 같은 이유다. */}
      <button
        type="button"
        onClick={onToggleOpen}
        aria-expanded={open}
        aria-label={`${group.label} ${formatAmount(group.sum)}원`}
        className="flex w-full items-center gap-2.5 px-4 py-3.5 text-left"
      >
        <span className="text-[15px] font-semibold">{group.label}</span>
        {today && <Badge variant="secondary">오늘</Badge>}
        <span className="ml-auto text-sm tabular-nums">
          {formatAmount(group.sum)}
        </span>
        {open ? (
          <ChevronDown className="text-muted-foreground size-4 shrink-0" />
        ) : (
          <ChevronRight className="text-muted-foreground size-4 shrink-0" />
        )}
      </button>

      {open &&
        (group.rows.length === 0 ? (
          <p className="text-muted-foreground px-4 pb-3.5 text-[13px]">
            아직 적은 게 없어요
          </p>
        ) : (
          <ul className="border-foreground/10 border-t pb-1">
            {group.rows.map((row) => (
              <li
                key={row.expenseId}
                className="flex items-center gap-1 pr-2 pl-0"
              >
                <ExpenseRowButton
                  row={row}
                  onEdit={onEdit}
                  disabled={offline}
                />
                {/*
                  지난 예정에만 붙는다. 실제로 결제가 일어났는지는 앱이 알 수 없으므로
                  올려 주는 배치를 두지 않고 사람이 누른다(§4.3).
                */}
                {row.overdue && (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    disabled={offline}
                    onClick={() => onConfirm(row)}
                  >
                    확정
                  </Button>
                )}
              </li>
            ))}
          </ul>
        ))}
    </section>
  );
}

function ExpenseRowButton({
  row,
  onEdit,
  disabled,
}: {
  row: ExpenseRow;
  onEdit: (row: ExpenseRow) => void;
  disabled: boolean;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onEdit(row)}
      className="hover:bg-muted flex min-h-11 flex-1 items-center gap-2 px-4 py-2.5 text-left transition-colors disabled:hover:bg-transparent"
    >
      <span className="truncate text-sm">{row.title ?? "제목 없음"}</span>
      {row.category && (
        <Badge variant="secondary">
          {EXPENSE_CATEGORY_LABELS[row.category]}
        </Badge>
      )}
      {/* 지난 예정은 「예정」이 아니라 「확정하세요」다 — 옆의 버튼이 그 말을 받는다. */}
      {row.status === "SCHEDULED" && (
        <Badge variant="outline">{row.overdue ? "지난 예정" : "예정"}</Badge>
      )}
      {/* 미분류는 경고가 아니라 할 일이다 — 「채우면 끝나요」가 상단 줄에 함께 있다. */}
      {row.uncategorized && <Badge variant="outline">정리 필요</Badge>}

      <span className="ml-auto flex items-baseline gap-1.5 tabular-nums">
        {/* 외화는 보조 표기다. 합계는 언제나 서버가 확정한 원화만 읽는다(§4.3). */}
        {row.fx && (
          <span className="text-muted-foreground text-[13px]">
            {row.fx.currency} {row.fx.amount.toLocaleString("ko-KR")}
          </span>
        )}
        {/* 환율을 못 받은 채 저장된 건. 0원이라고 말하면 거짓이다 — 채우라고 말한다. */}
        <span className="text-sm">
          {row.fx && row.fx.rate === null
            ? "환율 미입력"
            : `${formatAmount(row.amount)}원`}
        </span>
      </span>
    </button>
  );
}
