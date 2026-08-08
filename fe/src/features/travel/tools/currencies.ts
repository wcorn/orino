/**
 * 고를 수 있는 통화.
 *
 * <p>ECB 고시표에 <b>실제로 있는</b> 통화만 담는다 — 없는 통화를 고르게 해 두면 서버가
 * `TRAVEL-ERR-011`을 돌려주고, 사용자는 자기가 뭘 잘못했는지 알 수 없다.
 * 여행지로 자주 나오는 순으로 추렸다. (TWD·VND는 ECB가 고시하지 않아 뺐다.)
 */
export const CURRENCIES = [
  "JPY",
  "USD",
  "EUR",
  "CNY",
  "THB",
  "SGD",
  "HKD",
  "GBP",
  "AUD",
  "PHP",
  "MYR",
  "IDR",
  "INR",
  "CHF",
  "CAD",
  "NZD",
] as const;

export type Currency = (typeof CURRENCIES)[number];

const NAMES: Record<Currency, string> = {
  JPY: "일본 엔",
  USD: "미국 달러",
  EUR: "유로",
  CNY: "중국 위안",
  THB: "태국 바트",
  SGD: "싱가포르 달러",
  HKD: "홍콩 달러",
  GBP: "영국 파운드",
  AUD: "호주 달러",
  PHP: "필리핀 페소",
  MYR: "말레이시아 링깃",
  IDR: "인도네시아 루피아",
  INR: "인도 루피",
  CHF: "스위스 프랑",
  CAD: "캐나다 달러",
  NZD: "뉴질랜드 달러",
};

export function currencyName(code: string): string {
  return NAMES[code as Currency] ?? code;
}

/**
 * 여행 통화를 기본값으로 쓰되, 목록에 없으면 첫 통화로 떨어진다.
 *
 * <p>목록 밖 통화(TWD 등)를 그대로 넘기면 셀렉트가 빈 값이 되고 조회도 실패한다.
 * 여기서 한 번 걸러 화면이 항상 뭔가는 보여주게 한다.
 */
export function defaultCurrency(tripCurrency: string | undefined): Currency {
  const code = tripCurrency?.toUpperCase();
  return CURRENCIES.includes(code as Currency)
    ? (code as Currency)
    : CURRENCIES[0];
}
