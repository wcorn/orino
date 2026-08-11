/**
 * 목적지 언어 — <b>여행 타임존에서 유추</b>한다.
 *
 * <p>여행에 언어 필드가 없다. 통화로 유추하면 EUR처럼 여러 나라가 공유하는 경우가 있어 더
 * 부정확하고, 타임존은 나라와 거의 1:1이다.
 *
 * <p>완전한 표를 만들지 않는다 — 갈 만한 곳만 담고 나머지는 영어로 연다. 구글 번역은 어차피
 * 화면에서 언어를 바꿀 수 있어서, 틀려도 막다른 길이 아니다.
 */
const LANGUAGE_BY_TIMEZONE: Record<string, string> = {
  "Asia/Tokyo": "ja",
  "Asia/Seoul": "ko",
  "Asia/Shanghai": "zh-CN",
  "Asia/Taipei": "zh-TW",
  "Asia/Hong_Kong": "zh-TW",
  "Asia/Bangkok": "th",
  "Asia/Ho_Chi_Minh": "vi",
  "Asia/Jakarta": "id",
  "Asia/Manila": "tl",
  "Europe/Paris": "fr",
  "Europe/Berlin": "de",
  "Europe/Madrid": "es",
  "Europe/Rome": "it",
  "Europe/Lisbon": "pt",
  "Europe/Amsterdam": "nl",
};

/**
 * (v2.1) 목적지 언어는 <b>기준 도시의 국가</b>를 따라간다(§3.7).
 *
 * <p>국가 코드가 타임존보다 정확하다 — `Asia/Tokyo`는 일본에만 쓰이지만 `Asia/Bangkok`은
 * 베트남·캄보디아 일부도 쓰고, 도시를 옮기는 여행에서는 그 차이가 실제로 드러난다.
 * 국가를 모르면 타임존으로 떨어진다(그 전까지 쓰던 길이다).
 */
const LANGUAGE_BY_COUNTRY: Record<string, string> = {
  JP: "ja",
  KR: "ko",
  CN: "zh-CN",
  TW: "zh-TW",
  HK: "zh-TW",
  TH: "th",
  VN: "vi",
  ID: "id",
  PH: "tl",
  FR: "fr",
  DE: "de",
  ES: "es",
  IT: "it",
  PT: "pt",
  NL: "nl",
};

const FALLBACK = "en";

/**
 * 목적지 언어. <b>국가 → 타임존 → 영어</b> 순으로 찾는다.
 *
 * <p>틀려도 막다른 길이 아니다 — 구글 번역 화면에서 언어를 바꿀 수 있다.
 */
export function destinationLanguage(
  timezone: string,
  countryCode?: string | null,
): string {
  if (countryCode && LANGUAGE_BY_COUNTRY[countryCode]) {
    return LANGUAGE_BY_COUNTRY[countryCode];
  }
  return LANGUAGE_BY_TIMEZONE[timezone] ?? FALLBACK;
}

/** 한국어 → 목적지 언어. 앱이 없으면 웹으로 열린다. */
export function translateUrl(
  timezone: string,
  countryCode?: string | null,
): string {
  const params = new URLSearchParams({
    sl: "ko",
    tl: destinationLanguage(timezone, countryCode),
  });
  return `https://translate.google.com/?${params.toString()}`;
}
