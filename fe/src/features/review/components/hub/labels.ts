import type { SelectOption } from "@/components/ui/select";

import type {
  CardType,
  GradeFilter,
  UpcomingType,
  UpcomingWhen,
} from "../../api/reviewHub";
import type { Rating } from "../../api/reviews";

/** 종류 칩 라벨(BASIC/ORDERING/PAIR → 기본/순서/양방향). */
export const CARD_TYPE_LABEL: Record<CardType, string> = {
  BASIC: "기본",
  ORDERING: "순서",
  PAIR: "양방향",
};

type BadgeVariant = "destructive" | "warning" | "success" | "info";

/** 평가 Badge 색·라벨(다시=destructive / 어려움=warning / 양호=success / 쉬움=info). */
export const GRADE_BADGE: Record<
  Rating,
  { variant: BadgeVariant; label: string }
> = {
  AGAIN: { variant: "destructive", label: "다시" },
  HARD: { variant: "warning", label: "어려움" },
  GOOD: { variant: "success", label: "양호" },
  EASY: { variant: "info", label: "쉬움" },
};

export const MATERIAL_ALL = "all";

export const WHEN_OPTIONS: SelectOption<UpcomingWhen>[] = [
  { value: "all", label: "전체 기간" },
  { value: "today", label: "오늘" },
  { value: "3d", label: "3일" },
  { value: "7d", label: "7일" },
];

export const TYPE_OPTIONS: SelectOption<UpcomingType>[] = [
  { value: "all", label: "전체 종류" },
  { value: "basic", label: "기본" },
  { value: "order", label: "순서" },
  { value: "pair", label: "양방향" },
];

export const GRADE_OPTIONS: SelectOption<GradeFilter>[] = [
  { value: "all", label: "전체 평가" },
  { value: "AGAIN", label: "다시" },
  { value: "HARD", label: "어려움" },
  { value: "GOOD", label: "양호" },
  { value: "EASY", label: "쉬움" },
];
