import { Input } from "@/components/ui/input";

import type { SuggestionView } from "../api/ledger";
import { useTransactionSuggestions } from "../hooks/useLedgerQueries";
import { formatAmount } from "../lib/money";

interface TitleAutocompleteProps {
  value: string;
  onChange: (next: string) => void;
  /** 후보를 고르면 카테고리·자산·금액까지 함께 채운다. */
  onPick: (suggestion: SuggestionView) => void;
}

/**
 * 내용 입력 + 자동완성.
 *
 * <p>후보마다 <b>지난번의 `카테고리 · 자산`</b>을 우측에 보여준다. 같은 가맹점을 다시 적을 때
 * 사람이 다시 고르는 것은 그 둘이고, 30초 입력(확정 명세 §4.2)은 이런 것들이 모여야 성립한다.
 *
 * <p>목록을 <b>포커스 중에만</b> 띄운다. 항상 떠 있으면 아래 칸들을 가려 `Tab` 이동이 막힌다 —
 * 이 모달은 마우스 없이 끝나야 한다.
 */
export function TitleAutocomplete({
  value,
  onChange,
  onPick,
}: TitleAutocompleteProps) {
  const { data: suggestions } = useTransactionSuggestions(value);
  const visible = (suggestions ?? []).filter(
    (item) => item.title !== value.trim(),
  );

  return (
    <div className="relative">
      <label htmlFor="ledger-title" className="text-sm font-medium">
        내용
      </label>
      <Input
        id="ledger-title"
        autoComplete="off"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder="스타벅스 역삼"
        className="mt-1"
      />
      {visible.length > 0 && (
        <ul className="border-border bg-popover absolute z-10 mt-1 w-full overflow-hidden rounded-md border shadow-md">
          {visible.map((item) => (
            <li key={item.title}>
              <button
                type="button"
                // 이 버튼은 Tab 순서에서 빼 둔다 — 자동완성을 지나야 다음 칸에 닿는다면
                // 키보드 완결이 오히려 느려진다.
                tabIndex={-1}
                onClick={() => onPick(item)}
                className="hover:bg-muted flex w-full items-center justify-between gap-3 px-2.5 py-1.5 text-left text-sm transition-colors"
              >
                <span className="truncate">{item.title}</span>
                <span className="text-muted-foreground shrink-0 text-[13px]">
                  {[item.categoryName, item.assetName]
                    .filter(Boolean)
                    .join(" · ")}
                  <span className="ml-2 tabular-nums">
                    {formatAmount(item.amount)}
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
