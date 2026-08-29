import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { LoadingText } from "@/components/ui/loading-text";
import type { BalanceCurvePoint } from "@/features/ledger/api/ledger";
import { useBalanceCurve } from "@/features/ledger/hooks/useLedgerQueries";
import { formatBalance, formatDateHeader } from "@/features/ledger/lib/money";

const WIDTH = 640;
const HEIGHT = 140;

/**
 * 예상 잔액 곡선(`LDG-054` · 확정 명세 §8.4).
 *
 * <p><b>월말 숫자 하나로는 못 잡는 것을 잡는다.</b> 25일에 청약이 빠지고 나면 바닥인데
 * 월말에는 급여가 들어와 괜찮아 보이는 달이 있다 — 곡선은 그 사이를 보여준다.
 *
 * <p>0선을 언제나 그린다. 마이너스가 되는 달에만 그리면 <b>기준이 어디인지</b>가 그 달에만
 * 보이고, 그때는 이미 늦다.
 */
export function BalanceCurve({ days = 30 }: { days?: number }) {
  const { data, isPending, isError } = useBalanceCurve(days);

  if (isPending) {
    return <LoadingText />;
  }
  if (isError || !data || data.points.length === 0) {
    return null;
  }

  const balances = data.points.map((point) => point.balance);
  // 0선이 항상 화면 안에 들어오도록 범위에 0을 끼워 넣는다.
  const max = Math.max(...balances, 0);
  const min = Math.min(...balances, 0);
  const span = max - min || 1;

  const x = (index: number) =>
    (index / Math.max(data.points.length - 1, 1)) * WIDTH;
  const y = (balance: number) => HEIGHT - ((balance - min) / span) * HEIGHT;

  const line = data.points
    .map(
      (point, index) =>
        `${index === 0 ? "M" : "L"}${x(index)},${y(point.balance)}`,
    )
    .join(" ");
  const area = `${line} L${WIDTH},${y(min)} L0,${y(min)} Z`;
  const zeroY = y(0);
  const lowest = lowestPoint(data.points);

  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-3 rounded-xl p-5 ring-1">
      <header className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold">예상 잔액 곡선</h2>
        <span className="text-muted-foreground text-[13px]">
          지금 {formatBalance(data.currentBalance)} → {data.to}{" "}
          {formatBalance(data.points[data.points.length - 1].balance)}
        </span>
      </header>

      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={`앞으로 ${days}일 예상 잔액. 최저 ${formatBalance(data.minBalance.amount)}`}
        className="h-[140px] w-full"
        preserveAspectRatio="none"
      >
        <defs>
          <linearGradient id="balance-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.22" />
            <stop offset="100%" stopColor="var(--primary)" stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d={area} fill="url(#balance-fill)" />
        <path
          d={line}
          fill="none"
          stroke="var(--primary)"
          strokeWidth="2"
          vectorEffect="non-scaling-stroke"
        />
        {/* 0선. 여기 아래로 내려가면 통장이 비었다는 뜻이다. */}
        <line
          x1="0"
          y1={zeroY}
          x2={WIDTH}
          y2={zeroY}
          stroke="var(--destructive)"
          strokeWidth="1"
          strokeDasharray="4 4"
          vectorEffect="non-scaling-stroke"
        />
        {lowest && (
          <circle
            cx={x(lowest.index)}
            cy={y(lowest.point.balance)}
            r="3"
            fill="var(--warning)"
            vectorEffect="non-scaling-stroke"
          />
        )}
      </svg>

      {/*
        바닥이 언제·왜인지는 바로 아래 경고 카드가 말한다. 여기서 또 적으면 같은 사실이
        화면에 두 번 있고, 둘이 어긋나는 날 어느 쪽이 맞는지 알 수 없다.

        마이너스는 다른 사실이라 여기 남는다 — 「얼마까지 내려간다」와 「0을 넘어간다」는
        손쓸 방법이 다르다.
      */}
      {data.firstNegativeDate && (
        <Alert variant="destructive">
          <AlertTitle>
            잔액이 마이너스가 되는 날 —{" "}
            {formatDateHeader(data.firstNegativeDate)}
          </AlertTitle>
          <AlertDescription>
            <p>
              그 전에 예정을 옮기거나 미리 옮겨 둔 돈을 되돌려야 해요. 예정
              거래는 잔액을 바꾸지 않으니 지금 통장은 아직 멀쩡합니다.
            </p>
          </AlertDescription>
        </Alert>
      )}
    </section>
  );
}

function lowestPoint(points: BalanceCurvePoint[]) {
  let index = 0;
  for (let i = 1; i < points.length; i++) {
    if (points[i].balance < points[index].balance) {
      index = i;
    }
  }
  return points.length === 0 ? null : { index, point: points[index] };
}
