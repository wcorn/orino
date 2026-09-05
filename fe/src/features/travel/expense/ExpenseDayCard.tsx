import { ChevronDown, ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { formatAmount } from "@/features/ledger/lib/money";

import type { ExpenseGroup, ExpenseRow } from "../api/expenses";

interface ExpenseDayCardProps {
  group: ExpenseGroup;
  open: boolean;
  onToggleOpen: () => void;
  /** 오늘 묶음이면 헤더에 배지를 단다. 기본 펼침도 이 묶음 하나뿐이다(§10.2). */
  today: boolean;
}

/**
 * 날짜 묶음 카드(화면 §10.2). <b>준비의 분류 카드와 같은 껍데기·같은 헤더 버튼</b>이다 —
 * 두 화면이 같은 여행 안에서 나란히 쓰이므로 눌러야 열린다는 사실이 같아야 한다.
 *
 * <p><b>행 전체가 가계부 지출 상세 링크</b>다. 여행 안에 편집 화면을 두 벌 만들지
 * 않는다(D-35) — 여기서 고칠 수 있게 하면 같은 거래를 고치는 화면이 둘이 되고,
 * 그때부터 어느 쪽이 최신인지가 질문이 된다.
 */
export function ExpenseDayCard({
  group,
  open,
  onToggleOpen,
  today,
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
              <li key={row.transactionId}>
                <ExpenseRowLink row={row} />
              </li>
            ))}
          </ul>
        ))}
    </section>
  );
}

function ExpenseRowLink({ row }: { row: ExpenseRow }) {
  return (
    <Link
      to={`/ledger/transactions/${row.transactionId}`}
      className="hover:bg-muted flex min-h-11 items-center gap-2 px-4 py-2.5 transition-colors"
    >
      <span className="truncate text-sm">{row.title ?? "제목 없음"}</span>
      {row.status === "SCHEDULED" && <Badge variant="outline">예정</Badge>}
      {/* 미분류는 경고가 아니라 할 일이다 — 「채우면 끝나요」가 상단 줄에 함께 있다. */}
      {row.uncategorized && <Badge variant="outline">정리 필요</Badge>}

      <span className="ml-auto flex items-baseline gap-1.5 tabular-nums">
        {/* 외화는 보조 표기다. 합계는 언제나 서버가 확정한 원화만 읽는다(§4.3). */}
        {row.fx && (
          <span className="text-muted-foreground text-[13px]">
            {row.fx.currency} {row.fx.amount.toLocaleString("ko-KR")}
          </span>
        )}
        <span className="text-sm">{formatAmount(row.amount)}원</span>
      </span>
    </Link>
  );
}
