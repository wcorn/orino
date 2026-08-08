import { ArrowRightLeft } from "lucide-react";
import { useEffect, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { LoadingText } from "@/components/ui/loading-text";
import { Select } from "@/components/ui/select";
import type { ExchangeRate } from "@/features/travel/api/tools";
import {
  CURRENCIES,
  type Currency,
  currencyName,
} from "@/features/travel/tools/currencies";
import {
  convert,
  formatAmount,
  parseAmount,
} from "@/features/travel/tools/money";

interface ExchangeRateCardProps {
  rate: ExchangeRate | null;
  loading: boolean;
  online: boolean;
  currency: Currency;
  onCurrencyChange: (currency: Currency) => void;
}

const CURRENCY_OPTIONS = CURRENCIES.map((code) => ({
  value: code,
  label: `${code} · ${currencyName(code)}`,
}));

/** 현지에서 자주 쓰는 액수(§1.8). */
const PRESETS = [1000, 5000, 10000];

/**
 * 환율 계산기(§S-08).
 *
 * <p><b>양방향이다.</b> 어느 칸에 쳐도 반대가 따라온다 — 현지에서 "10만원이면 몇 엔이지"와
 * "3,000엔이면 얼마지"가 둘 다 필요하고, 한 방향만 되면 계산기를 따로 켜게 된다.
 */
export function ExchangeRateCard({
  rate,
  loading,
  online,
  currency,
  onCurrencyChange,
}: ExchangeRateCardProps) {
  const [baseInput, setBaseInput] = useState("");
  const [quoteInput, setQuoteInput] = useState("");

  // 환율이 바뀌면(통화 변경·재조회) 계산을 다시 맞춘다.
  useEffect(() => {
    setBaseInput("");
    setQuoteInput("");
  }, [rate?.base, rate?.quote]);

  const setFromBase = (input: string) => {
    setBaseInput(input);
    const amount = parseAmount(input);
    setQuoteInput(
      amount === null || !rate ? "" : formatAmount(convert(amount, rate.rate)),
    );
  };

  const setFromQuote = (input: string) => {
    setQuoteInput(input);
    const amount = parseAmount(input);
    setBaseInput(
      amount === null || !rate
        ? ""
        : formatAmount(convert(amount, 1 / rate.rate)),
    );
  };

  return (
    <section className="border-border bg-card flex flex-col gap-3 rounded-xl border p-4">
      <div className="flex items-center gap-2">
        <h2 className="text-heading flex-1 font-medium">환율</h2>
        {/* 캐시된 값이라는 걸 숨기지 않는다 — 환율은 돈이 걸린 숫자다. */}
        {!online && <Badge variant="secondary">최신 아님</Badge>}
        {/* 여행 통화가 기본이지만 경유지에서 다른 돈을 쓰기도 한다. */}
        <span id="fx-currency-label" className="sr-only">
          기준 통화
        </span>
        <Select
          value={currency}
          onValueChange={onCurrencyChange}
          options={CURRENCY_OPTIONS}
          ariaLabelledby="fx-currency-label"
          disabled={!online}
        />
      </div>

      {loading ? (
        <LoadingText />
      ) : !rate ? (
        <p className="text-muted-foreground text-sm">
          환율을 가져오지 못했어요.
        </p>
      ) : (
        <>
          <div className="flex items-center gap-2">
            <label className="flex-1">
              <span className="text-muted-foreground text-xs">{rate.base}</span>
              <Input
                inputMode="numeric"
                value={baseInput}
                onChange={(e) => setFromBase(e.target.value)}
                aria-label={`${rate.base} 금액`}
                placeholder="0"
              />
            </label>
            <ArrowRightLeft className="text-muted-foreground mt-4 size-4 shrink-0" />
            <label className="flex-1">
              <span className="text-muted-foreground text-xs">
                {rate.quote}
              </span>
              <Input
                inputMode="numeric"
                value={quoteInput}
                onChange={(e) => setFromQuote(e.target.value)}
                aria-label={`${rate.quote} 금액`}
                placeholder="0"
              />
            </label>
          </div>

          <div className="flex flex-wrap gap-1.5">
            {PRESETS.map((amount) => (
              <button
                key={amount}
                type="button"
                onClick={() => setFromBase(String(amount))}
                className="border-border hover:bg-accent rounded-full border px-3 py-1 text-[13px] tabular-nums"
              >
                {formatAmount(amount)} {rate.base}
              </button>
            ))}
          </div>

          <p className="text-muted-foreground text-xs">
            <span className={online ? "" : "opacity-60"}>
              ECB 기준 · {rate.referenceDate}
              {online ? "" : " (오프라인 캐시)"}
            </span>{" "}
            · 실제 결제 환율과 다를 수 있습니다
          </p>
        </>
      )}
    </section>
  );
}
