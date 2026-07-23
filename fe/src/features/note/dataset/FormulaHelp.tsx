interface Row {
  /** 문법·예시(모노). */
  code: string;
  /** 설명. */
  desc: string;
}

interface Section {
  title: string;
  rows: Row[];
}

/**
 * 수식 도움말 내용. 엔진(FormulaParser)이 실제로 받는 문법만 싣는다 — 여기와 엔진이 어긋나면
 * 사용자가 안 되는 걸 쓰게 된다. 함수명은 엔진 그대로(AVG 등).
 */
const SECTIONS: Section[] = [
  {
    title: "기본",
    rows: [
      {
        code: "= {단가} * {수량}",
        desc: "= 로 시작하고, 열은 이름을 {} 로 감싼다",
      },
    ],
  },
  {
    title: "셀 참조",
    rows: [
      { code: "{과목}", desc: "같은 행의 그 열" },
      { code: "{점수}2", desc: "2행의 그 열(행 번호로 콕 집기)" },
      {
        code: "{요약!환율}1",
        desc: "다른 표(요약)의 1행 환율 — 표에 이름이 있어야 함",
      },
    ],
  },
  {
    title: "산술",
    rows: [
      { code: "+  -  *  /", desc: "사칙연산, 괄호로 우선순위" },
      { code: "= ({단가} + {배송}) * {수량}", desc: "" },
    ],
  },
  {
    title: "비교 (참/거짓)",
    rows: [
      { code: "=  <>  <  >  <=  >=", desc: "" },
      { code: "= {점수} >= 80", desc: "" },
    ],
  },
  {
    title: "조건",
    rows: [
      {
        code: '= IF({통화}="엔", {금액}*{환율}, {금액})',
        desc: "조건이 참이면 둘째, 거짓이면 셋째",
      },
      { code: "AND(...)  OR(...)  NOT(...)", desc: "여러 조건을 묶는다" },
    ],
  },
  {
    title: "집계 (열 전체)",
    rows: [
      { code: "= SUM({금액})", desc: "합계 — 숫자만 더한다" },
      { code: "AVG  COUNT  MIN  MAX", desc: "평균·개수·최소·최대" },
    ],
  },
  {
    title: "조건부 집계",
    rows: [
      {
        code: '= SUMIF({분류}, "교통", {금액})',
        desc: "분류가 '교통'인 행의 금액 합",
      },
      { code: '= COUNTIF({분류}, "식사")', desc: "분류가 '식사'인 행 수" },
    ],
  },
  {
    title: "표간 참조",
    rows: [
      { code: "= SUM({도쿄!금액})", desc: "다른 표(도쿄)의 열 합계" },
      { code: "= {요약!환율}1", desc: "다른 표의 특정 셀" },
    ],
  },
  {
    title: "함수",
    rows: [{ code: "= ABS({차이})", desc: "절댓값" }],
  },
  {
    title: "에러값",
    rows: [
      { code: "#REF!", desc: "참조가 끊김(열·행·표가 사라짐)" },
      { code: "#DIV/0!", desc: "0으로 나눔" },
      { code: "#VALUE!", desc: "숫자가 아닌 걸 계산" },
    ],
  },
];

/** 수식 도움말 패널(표시 전용). 문법·예시를 카테고리별로 보여준다. 바깥 클릭·닫기로 닫는다. */
export function FormulaHelp({ onClose }: { onClose: () => void }) {
  return (
    <>
      <div className="fixed inset-0 z-40" onClick={onClose} />
      <div
        role="dialog"
        aria-label="수식 도움말"
        className="border-border bg-popover fixed top-1/2 left-1/2 z-50 max-h-[80vh] w-80 -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-md border p-3 shadow-md"
      >
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-sm font-medium">수식 도움말</h2>
          <button
            type="button"
            aria-label="도움말 닫기"
            onClick={onClose}
            className="text-muted-foreground hover:text-foreground text-xs"
          >
            닫기
          </button>
        </div>
        <div className="flex flex-col gap-3">
          {SECTIONS.map((section) => (
            <section key={section.title}>
              <h3 className="text-muted-foreground mb-1 text-xs font-medium">
                {section.title}
              </h3>
              <div className="flex flex-col gap-1">
                {section.rows.map((row) => (
                  <div key={row.code}>
                    <code className="bg-muted rounded px-1 py-0.5 text-xs">
                      {row.code}
                    </code>
                    {row.desc && (
                      <span className="text-muted-foreground ml-2 text-xs">
                        {row.desc}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </section>
          ))}
        </div>
      </div>
    </>
  );
}
