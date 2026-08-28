import { Input } from "@/components/ui/input";
import { Menu, MenuItem, MenuSeparator } from "@/components/ui/menu";

import { CALCULATOR_KEYS, evaluate, hasOperator } from "../lib/calculator";
import { formatAmount } from "../lib/money";

/** 먼저 보여줄 통화. 나머지는 직접 입력한다(화면 설계 §11). */
const QUICK_CURRENCIES = ["JPY", "USD", "EUR"];

interface AmountFieldProps {
  /** 계산기 수식 그대로. `12000+3000`처럼 연산자가 섞여 있을 수 있다. */
  expression: string;
  onExpressionChange: (next: string) => void;
  currency: string;
  onCurrencyChange: (next: string) => void;
  /** 외화일 때의 환율. 못 가져왔으면 `null`이고, 그때도 저장은 막지 않는다. */
  rate: number | null;
  onRateChange: (next: number | null) => void;
  /** 환율 조회가 끝났는지. 조회 중에는 「못 가져왔다」고 단정하지 않는다. */
  rateLoading: boolean;
  rateReferenceDate: string | null;
}

/**
 * 금액 입력 — 이 모달에서 가장 큰 칸이다.
 *
 * <p>하루 30초 입력의 대부분이 이 칸에서 끝난다(확정 명세 §4.2). 그래서 숫자가 크고,
 * 계산기 행이 붙어 있고, 통화 버튼이 바로 옆에 있다.
 *
 * <p>외화를 고르면 <b>원화 환산액이 회색으로 따라 붙는다</b>. 환율은 <b>수정 가능한 입력</b>이다 —
 * 카드사가 실제 적용한 환율이 고시와 다르면 나중에 고쳐야 하기 때문이다.
 * <b>환율을 못 가져와도 저장을 막지 않는다</b>: 안내만 띄우고 원화 금액을 직접 받는다.
 */
export function AmountField({
  expression,
  onExpressionChange,
  currency,
  onCurrencyChange,
  rate,
  onRateChange,
  rateLoading,
  rateReferenceDate,
}: AmountFieldProps) {
  const value = evaluate(expression);
  const foreign = currency !== "KRW";
  const krw =
    foreign && value !== null && rate !== null
      ? Math.round(value * rate)
      : null;

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-end gap-2">
        <div className="min-w-0 flex-1">
          <label htmlFor="ledger-amount" className="text-sm font-medium">
            금액
          </label>
          <Input
            id="ledger-amount"
            // 숫자 키패드를 띄우되 계산기 기호도 받아야 해서 type=text다.
            inputMode="decimal"
            autoComplete="off"
            // 모달이 열리면 바로 숫자를 칠 수 있어야 한다 — 30초 입력은 여기서 시작한다.
            autoFocus
            value={expression}
            onChange={(event) => onExpressionChange(event.target.value)}
            placeholder="0"
            className="mt-1 h-12 text-[28px] font-semibold tabular-nums"
          />
        </div>
        <Menu
          align="end"
          trigger={
            <button
              type="button"
              aria-label={`통화 — 현재 ${currency === "KRW" ? "원" : currency}`}
              className="border-input bg-background hover:bg-muted h-12 shrink-0 rounded-lg border px-3 text-sm font-medium transition-colors"
            >
              {currency === "KRW" ? "원" : currency}
            </button>
          }
        >
          <MenuItem onClick={() => onCurrencyChange("KRW")}>원 (KRW)</MenuItem>
          <MenuSeparator />
          {QUICK_CURRENCIES.map((code) => (
            <MenuItem key={code} onClick={() => onCurrencyChange(code)}>
              {code}
            </MenuItem>
          ))}
        </Menu>
      </div>

      {/*
        계산기 행 — 영수증 여러 장을 더해 넣는 자리다.

        Tab 순서에서는 빼 둔다. 키보드로 쓰는 사람은 금액 칸에 `+`를 직접 치면 되고,
        버튼 넷이 순서에 끼면 금액에서 날짜까지 네 번을 더 눌러야 한다 —
        「Tab 이동 → Enter 저장」이 그만큼 멀어진다.
      */}
      <div className="flex gap-1.5">
        {CALCULATOR_KEYS.map((key) => (
          <button
            key={key}
            type="button"
            tabIndex={-1}
            aria-label={`계산기 ${key}`}
            onClick={() => onExpressionChange(expression + key)}
            className="border-border text-muted-foreground hover:bg-muted h-8 w-9 rounded-md border text-sm transition-colors"
          >
            {key}
          </button>
        ))}
        {hasOperator(expression) && (
          <span className="text-muted-foreground self-center text-[13px] tabular-nums">
            = {value === null ? "계산할 수 없어요" : formatAmount(value)}
          </span>
        )}
      </div>

      {foreign && (
        <div className="flex flex-col gap-1.5">
          <div className="flex items-center gap-2">
            <label
              htmlFor="ledger-fx-rate"
              className="text-muted-foreground text-[13px]"
            >
              환율
            </label>
            <Input
              id="ledger-fx-rate"
              inputMode="decimal"
              autoComplete="off"
              value={rate ?? ""}
              onChange={(event) => {
                const next = event.target.value.trim();
                onRateChange(next === "" ? null : Number(next));
              }}
              placeholder="예: 8.7604"
              className="h-8 w-32 tabular-nums"
            />
            <span className="text-muted-foreground text-[13px]">
              KRW/{currency}
            </span>
          </div>
          {krw !== null ? (
            // 본문 금액은 저장 후 서버가 준 값으로 갈린다. 이 줄은 입력 중의 미리보기다.
            <p className="text-muted-foreground text-[13px] tabular-nums">
              ≈ {formatAmount(krw)}원
              {rateReferenceDate && ` · ECB ${rateReferenceDate} 고시`}
            </p>
          ) : (
            !rateLoading && (
              <p className="text-muted-foreground text-[13px]">
                환율을 가져오지 못했어요. 직접 넣거나, 원화로 바꿔 금액만 적어도
                저장됩니다.
              </p>
            )
          )}
        </div>
      )}
    </div>
  );
}
