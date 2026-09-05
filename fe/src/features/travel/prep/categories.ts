import {
  CalendarCheck,
  FileText,
  ListChecks,
  type LucideIcon,
  Luggage,
} from "lucide-react";

import type { PrepCategory } from "../api/prep";

/**
 * 네 분류의 이름과 아이콘(§11). <b>다섯 번째를 만들지 않는다.</b>
 *
 * <p>서버가 그룹을 네 개 다 내려주므로 화면은 목록을 따로 들지 않는다 — 여기 있는 것은
 * 「무엇이 있는가」가 아니라 「그것을 어떻게 부르고 그리는가」다.
 */
export const PREP_CATEGORY_LABEL: Record<PrepCategory, string> = {
  DOCUMENT: "서류",
  BOOKING: "예약",
  BAG: "짐",
  TODO: "할 일",
};

export const PREP_CATEGORY_ICON: Record<PrepCategory, LucideIcon> = {
  DOCUMENT: FileText,
  BOOKING: CalendarCheck,
  BAG: Luggage,
  TODO: ListChecks,
};

/**
 * 처음 펼쳐 두는 분류(§10.7). 서류는 대개 한 번 챙기면 끝이고, 짐과 예약이 출발 전날까지
 * 계속 손이 가는 곳이다.
 *
 * <p>접힘 상태를 URL에 넣지 않는다 — 분류가 넷이라 파라미터가 화면보다 커진다.
 * 새로고침하면 여기로 돌아온다.
 */
export const PREP_DEFAULT_OPEN: PrepCategory[] = ["BOOKING", "BAG"];
