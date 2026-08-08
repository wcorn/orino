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

const FALLBACK = "en";

export function destinationLanguage(timezone: string): string {
  return LANGUAGE_BY_TIMEZONE[timezone] ?? FALLBACK;
}

/** 한국어 → 목적지 언어. 앱이 없으면 웹으로 열린다. */
export function translateUrl(timezone: string): string {
  const params = new URLSearchParams({
    sl: "ko",
    tl: destinationLanguage(timezone),
  });
  return `https://translate.google.com/?${params.toString()}`;
}
