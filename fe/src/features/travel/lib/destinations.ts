/**
 * 1단계 목적지 입력용 선택지.
 *
 * <p>2단계에서 목적지를 검색(Google Places)으로 고르면 서버가 좌표에서 타임존을, 국가 코드에서
 * 통화를 정해 준다. 그때까지는 직접 고르므로, 전체 IANA 목록(400여 개) 대신 갈 만한 곳만
 * 추린다 — 고르는 데 걸리는 시간이 곧 이 화면의 비용이다.
 */
export const TIMEZONE_OPTIONS = [
  { value: "Asia/Seoul", label: "Asia/Seoul (한국)" },
  { value: "Asia/Tokyo", label: "Asia/Tokyo (일본)" },
  { value: "Asia/Shanghai", label: "Asia/Shanghai (중국)" },
  { value: "Asia/Taipei", label: "Asia/Taipei (대만)" },
  { value: "Asia/Hong_Kong", label: "Asia/Hong_Kong (홍콩)" },
  { value: "Asia/Singapore", label: "Asia/Singapore (싱가포르)" },
  { value: "Asia/Bangkok", label: "Asia/Bangkok (태국·베트남)" },
  { value: "Asia/Manila", label: "Asia/Manila (필리핀)" },
  { value: "Asia/Jakarta", label: "Asia/Jakarta (인도네시아)" },
  { value: "Australia/Sydney", label: "Australia/Sydney (호주 동부)" },
  { value: "Pacific/Auckland", label: "Pacific/Auckland (뉴질랜드)" },
  { value: "Pacific/Honolulu", label: "Pacific/Honolulu (하와이)" },
  { value: "America/Los_Angeles", label: "America/Los_Angeles (미국 서부)" },
  { value: "America/New_York", label: "America/New_York (미국 동부)" },
  { value: "Europe/London", label: "Europe/London (영국)" },
  { value: "Europe/Paris", label: "Europe/Paris (서유럽)" },
  { value: "Europe/Rome", label: "Europe/Rome (이탈리아)" },
  { value: "Europe/Madrid", label: "Europe/Madrid (스페인)" },
] as const;

export const CURRENCY_OPTIONS = [
  { value: "KRW", label: "KRW · 원" },
  { value: "JPY", label: "JPY · 엔" },
  { value: "USD", label: "USD · 달러" },
  { value: "EUR", label: "EUR · 유로" },
  { value: "GBP", label: "GBP · 파운드" },
  { value: "CNY", label: "CNY · 위안" },
  { value: "TWD", label: "TWD · 대만 달러" },
  { value: "HKD", label: "HKD · 홍콩 달러" },
  { value: "SGD", label: "SGD · 싱가포르 달러" },
  { value: "THB", label: "THB · 바트" },
  { value: "VND", label: "VND · 동" },
  { value: "PHP", label: "PHP · 페소" },
  { value: "IDR", label: "IDR · 루피아" },
  { value: "AUD", label: "AUD · 호주 달러" },
  { value: "NZD", label: "NZD · 뉴질랜드 달러" },
] as const;

/** 여행 단위 기본 알림 시점(분 전). */
export const NOTIFY_MINUTES_OPTIONS = [
  { value: "5", label: "5분 전" },
  { value: "10", label: "10분 전" },
  { value: "15", label: "15분 전" },
  { value: "30", label: "30분 전" },
  { value: "60", label: "1시간 전" },
] as const;

export const DEFAULT_NOTIFY_MINUTES = 15;
