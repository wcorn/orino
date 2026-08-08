import { client } from "@/shared/api";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/** 서버가 WMO 코드를 네 갈래로 줄여 준다 — 화면은 코드를 모른다. */
export type WeatherIcon = "CLEAR" | "CLOUD" | "RAIN" | "SNOW";

export interface DailyWeather {
  date: string;
  icon: WeatherIcon;
  tempMax: number | null;
  tempMin: number | null;
  /** 강수확률(%). 60 이상 강조는 화면 규칙이다(§1.8). */
  precipProbability: number | null;
}

export interface HourlyWeather {
  /** 여행 타임존의 벽시계 시각("09:00"). */
  time: string;
  icon: WeatherIcon;
  temp: number | null;
}

export interface WeatherForecast {
  source: string;
  license: string;
  fetchedAt: string;
  /** 예보 범위(16일) 밖 날짜는 아예 없다 — 빈 배열이 정상이다. */
  daily: DailyWeather[];
  hourly: Record<string, HourlyWeather[]>;
}

export async function fetchWeather(tripId: number): Promise<WeatherForecast> {
  const { data } = await client.get<ApiEnvelope<WeatherForecast>>(
    `/travel/trips/${tripId}/weather`,
  );
  return data.data;
}

export interface ExchangeRate {
  base: string;
  quote: string;
  /** 1 base당 quote 금액. */
  rate: number;
  source: string;
  /** ECB 고시일. 주말·공휴일엔 직전 영업일이다. */
  referenceDate: string;
  fetchedAt: string;
}

export async function fetchExchangeRate(
  base: string,
  quote: string,
): Promise<ExchangeRate> {
  const { data } = await client.get<ApiEnvelope<ExchangeRate>>("/travel/fx", {
    params: { base, quote },
  });
  return data.data;
}
