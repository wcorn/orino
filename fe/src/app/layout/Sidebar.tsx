import { useQueryClient } from "@tanstack/react-query";
import {
  BookOpen,
  CalendarClock,
  CalendarDays,
  Camera,
  ChartColumn,
  Check,
  CheckSquare,
  ChevronDown,
  ChevronRight,
  ChevronsUpDown,
  CreditCard,
  FileText,
  Home,
  LayoutGrid,
  Link2,
  Plane,
  Plus,
  ReceiptText,
  Repeat,
  Settings,
  SquareCheckBig,
  Star,
  Tag,
  Target,
  Upload,
  Wallet,
  Wrench,
} from "lucide-react";
import { Fragment, useEffect } from "react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";

import { Menu, MenuItem, MenuSeparator } from "@/components/ui/menu";
import { useLedgerSummary } from "@/features/ledger/hooks/useLedgerQueries";
import { useReviewSummary } from "@/features/review/hooks/useReviewSummary";
import { useLinks } from "@/features/shortlink/hooks/useLinks";
import { useShortlinkTags } from "@/features/shortlink/hooks/useShortlinkTags";
import type {
  SidebarTripSummary,
  TravelSummary,
} from "@/features/travel/api/travel";
import { useTravelSummary } from "@/features/travel/hooks/useTravelSummary";
import { readLastTrip, rememberTrip } from "@/features/travel/lib/lastTrip";
import { travelKeys } from "@/features/travel/queryKeys";
import { cn } from "@/lib/utils";

interface NavItem {
  to: string;
  label: string;
  icon: typeof Home;
  /**
   * 항목을 활성으로 볼 추가 경로. 지정하면 NavLink 기본 매칭 대신 이 경로들로 판정한다.
   * (「플래너」처럼 상위 1탭이 여러 하위 라우트를 대표하는 경우)
   */
  activePaths?: string[];
  /**
   * 경로 접두어로 표현할 수 없는 활성 판정. 여행 쪽은 `/travel/trips/:id/board`처럼
   * 중간에 id가 끼어 접두어 비교로는 「여행 목록」과 구분되지 않는다.
   */
  matchPath?: (pathname: string) => boolean;
  /**
   * 검색 파라미터까지 봐야 하는 판정. 링크 쪽 「즐겨찾기」는 별도 라우트가 아니라
   * 같은 목록의 필터(`/links?favorite=1`)라, pathname만으로는 「링크 목록」과 구분되지 않는다.
   */
  matchLocation?: (pathname: string, search: string) => boolean;
}

/** `/travel/trips/12/board` · `/travel/trips/12/map` */
const BOARD_PATH = /^\/travel\/trips\/\d+\/(board|map)$/;
/** `/travel/trips` · `/travel/trips/new` · `/travel/trips/12/edit` (목록과 생성·수정 폼) */
const TRIP_LIST_PATH = /^\/travel\/trips(\/new|\/\d+\/edit)?$/;
/** `/travel/trips/12/prep` */
const PREP_PATH = /^\/travel\/trips\/\d+\/prep$/;
/** `/travel/trips/12/expenses` — 화면은 #1329에서 채운다. */
const EXPENSES_PATH = /^\/travel\/trips\/\d+\/expenses$/;
/** `/ledger/cards` · `/ledger/cards/12/statements` (카드 목록과 그 카드의 청구서) */
const LEDGER_CARD_PATH = /^\/ledger\/cards(\/\d+\/statements)?$/;

const DAILY_NAV_ITEMS: NavItem[] = [
  { to: "/home", label: "홈", icon: Home },
  { to: "/planner/materials", label: "학습 자료", icon: BookOpen },
  { to: "/notes", label: "노트", icon: FileText },
  { to: "/lifelog", label: "일상기록", icon: Camera },
  { to: "/planner/reviews", label: "복습", icon: CheckSquare },
  {
    to: "/planner/calendar",
    label: "플래너",
    icon: CalendarDays,
    activePaths: ["/planner/calendar", "/planner/plan", "/planner/routines"],
  },
  { to: "/integrations", label: "연동 설정", icon: Settings },
];

/**
 * 여행 무관 항목 — 트리 <b>위</b>에 온다. 보드·준비·경비는 여기 없다. 셋은 여행 하나에
 * 매달린 화면이라 트리 안에서 그 여행의 자식으로 산다(화면 §10.8).
 */
const TRAVEL_TOP_ITEMS: NavItem[] = [
  { to: "/travel", label: "홈", icon: Home },
  {
    to: "/travel/trips",
    label: "여행 목록",
    icon: Plane,
    matchPath: (pathname) => TRIP_LIST_PATH.test(pathname),
  },
];

/** 트리 <b>아래</b>, 구분선 다음. 여행과 무관한 도구·설정이다. */
const TRAVEL_BOTTOM_ITEMS: NavItem[] = [
  { to: "/travel/tools", label: "도구", icon: Wrench },
  { to: "/travel/settings", label: "설정", icon: Settings },
];

/** 여행 하나가 펼쳐 보여 주는 탭 셋. 순서가 곧 트리에 그려지는 순서다. */
const TRIP_TABS = [
  { key: "board", label: "일정 보드", icon: CalendarDays },
  { key: "prep", label: "준비", icon: SquareCheckBig },
  { key: "expenses", label: "경비", icon: Wallet },
] as const;

type TripTab = (typeof TRIP_TABS)[number]["key"];

/**
 * 트리를 접어 드롭다운으로 바꾸는 문턱. 진행 중·예정이 이보다 많으면 사이드바가 트리만으로
 * 화면을 채운다 — 현실적으로 생기지 않지만 무너지지는 않게 한다(프레임 `3b`).
 */
const TREE_DROPDOWN_THRESHOLD = 6;

/**
 * 지금 URL이 가리키는 여행 id. <b>선택된 여행의 진실은 URL이다</b> — 여기서 못 읽으면
 * 기본 여행으로 내려간다(D-37).
 */
function tripIdOf(pathname: string): number | null {
  const matched = /^\/travel\/trips\/(\d+)\//.exec(pathname);
  return matched ? Number(matched[1]) : null;
}

/**
 * 지금 보고 있는 탭. 여행을 바꿔도 <b>이 탭을 유지</b>한다 — 두 여행의 준비를 오가는 것이
 * 클릭 한 번이어야 한다.
 *
 * <p>일정 상세(`/travel/activities/:id`)는 URL에 여행 id가 없다. 그래도 보드에서 들어온
 * 화면이라 「일정 보드」로 친다 — 여기서 null을 주면 상세를 보는 동안 트리가 아무 탭도
 * 가리키지 않는다.
 */
function tripTabOf(pathname: string): TripTab | null {
  if (BOARD_PATH.test(pathname) || pathname.startsWith("/travel/activities/")) {
    return "board";
  }
  if (PREP_PATH.test(pathname)) return "prep";
  if (EXPENSES_PATH.test(pathname)) return "expenses";
  return null;
}

/** 「4일차」 / 「D-49」 / 「D-day」. 진행 중과 예정이 같은 자리를 나눠 쓴다. */
function tripBadgeOf(trip: SidebarTripSummary): string | null {
  if (trip.dayNumber != null) return `${trip.dayNumber}일차`;
  if (trip.dDay == null) return null;
  return trip.dDay === 0 ? "D-day" : `D-${trip.dDay}`;
}

/**
 * 링크 메뉴. 「즐겨찾기」는 별도 라우트가 아니라 목록의 필터다 — 즐겨찾기만 보는 화면을
 * 따로 만들면 같은 카드가 두 화면에 살고, 발급·편집 후 무엇을 갱신할지가 둘로 갈린다.
 */
const LINK_NAV_ITEMS: NavItem[] = [
  {
    to: "/links",
    label: "링크 목록",
    icon: Link2,
    // 상세(`/links/{slug}`)도 이 항목이 대표한다.
    matchLocation: (pathname, search) =>
      (pathname === "/links" && !isFavoriteFilter(search)) ||
      pathname.startsWith("/links/"),
  },
  {
    to: "/links?favorite=1",
    label: "즐겨찾기",
    icon: Star,
    matchLocation: (pathname, search) =>
      pathname === "/links" && isFavoriteFilter(search),
  },
];

/**
 * 가계부 메뉴(화면 설계 §2.2). 스타일은 위 세 세트와 <b>100% 동일</b>하다.
 *
 * <p>우측 숫자(미분류·미납·정기 항목 수)는 여기 붙지 않는다 — `GET /api/ledger/summary`가
 * 아직 없다. `0`을 그려 두면 「없다」와 「모른다」가 같아 보이므로, 숫자는 BE가 생긴 뒤
 * 붙인다(#1264 · #1265).
 */
const LEDGER_NAV_ITEMS: NavItem[] = [
  { to: "/ledger", label: "홈", icon: Home },
  { to: "/ledger/transactions", label: "내역", icon: ReceiptText },
  { to: "/ledger/upcoming", label: "예정", icon: CalendarClock },
  {
    to: "/ledger/assets",
    label: "자산",
    icon: Wallet,
    // 자산 상세(`/ledger/assets/:id`)도 이 항목이 대표한다.
    activePaths: ["/ledger/assets"],
  },
  {
    to: "/ledger/cards",
    label: "카드 청구서",
    icon: CreditCard,
    // 청구서는 `/ledger/cards/12/statements`라 접두어 비교로 목록과 구분되지 않는다.
    matchPath: (pathname) => LEDGER_CARD_PATH.test(pathname),
  },
  { to: "/ledger/recurring", label: "정기 항목", icon: Repeat },
  { to: "/ledger/budget", label: "예산", icon: Target },
  { to: "/ledger/stats", label: "통계", icon: ChartColumn },
  { to: "/ledger/import", label: "가져오기", icon: Upload },
  { to: "/ledger/settings", label: "설정", icon: Settings },
];

function isFavoriteFilter(search: string): boolean {
  return new URLSearchParams(search).get("favorite") === "1";
}

/** 여행 워크스페이스인지 — 경로 하나로 판정한다(별도 상태를 두면 새로고침에 어긋난다). */
function isTravelWorkspace(pathname: string): boolean {
  return pathname === "/travel" || pathname.startsWith("/travel/");
}

/**
 * 링크 워크스페이스인지. 여행과 같은 방식으로 <b>경로 하나로만</b> 판정한다 —
 * 상태를 따로 들면 새로고침·뒤로가기에서 어긋난다.
 */
function isLinkWorkspace(pathname: string): boolean {
  return pathname === "/links" || pathname.startsWith("/links/");
}

/** 가계부 워크스페이스인지. 위 둘과 같은 방식 — 경로 하나로만 판정한다. */
function isLedgerWorkspace(pathname: string): boolean {
  return pathname === "/ledger" || pathname.startsWith("/ledger/");
}

/** activePaths가 지정된 항목의 활성 여부 — 해당 경로이거나 그 하위 경로면 활성. */
function matchesActivePaths(pathname: string, paths: string[]): boolean {
  return paths.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

/** 「홈」이 여럿이라 접두어 매칭으로는 하위 경로에서도 활성이 된다. 그 셋만 정확히 일치시킨다. */
const EXACT_MATCH_PATHS = ["/home", "/travel", "/ledger"];

type WorkspaceKey = "travel" | "daily" | "link" | "ledger";

const WORKSPACE_LABELS: Record<WorkspaceKey, string> = {
  travel: "여행",
  daily: "일상",
  link: "링크",
  ledger: "가계부",
};

const WORKSPACE_ICONS: Record<WorkspaceKey, typeof Home> = {
  travel: Plane,
  daily: LayoutGrid,
  link: Link2,
  ledger: Wallet,
};

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

export function Sidebar({ open, onClose }: SidebarProps) {
  const { pathname, search } = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const travel = isTravelWorkspace(pathname);
  const link = isLinkWorkspace(pathname);
  const ledger = isLedgerWorkspace(pathname);

  const { data: reviewData } = useReviewSummary();
  const reviewCount = reviewData?.counts.now ?? 0;
  // 훅은 항상 호출하되(조건부 호출 금지) 다른 워크스페이스에서는 요청을 끈다 —
  // 일상 화면이 여행·링크 API를 부르기 시작하면 그건 일상 쪽 동작 변경이다.
  const { data: travelData } = useTravelSummary({ enabled: travel });
  const trips = travelData?.trips ?? [];
  /**
   * 펼칠 여행. <b>URL이 진실이고</b>, 여행 id가 없는 진입에서만 기본 여행으로 내려간다 —
   * 진행 중 → 다음 예정 순이다(D-37 · 마지막 본 여행은 #1347).
   *
   * <p>URL의 id가 요약에 없으면(삭제된 여행·남의 여행) 그대로 두지 않고 기본 여행으로
   * 내려간다. 죽은 id를 펼치면 트리에 아무 줄도 선택되지 않는다.
   */
  const urlTripId = tripIdOf(pathname);
  /*
    URL이 여행을 말해 주면 그것을 기억한다. 사용자가 실제로 고른 것만 남기려고 URL만 본다 —
    기본 여행(진행 중·예정)까지 저장하면 「마지막으로 본 여행」이 한 번도 안 고른 여행을
    가리킨다. 읽는 쪽은 폴백 하나뿐이고, 진실은 언제나 URL이다(D-37).
  */
  useEffect(() => {
    if (urlTripId !== null) rememberTrip(urlTripId);
  }, [urlTripId]);

  const selectedTripId =
    [urlTripId, travelData?.ongoing?.id, travelData?.next?.id].find((id) =>
      trips.some((trip) => trip.id === id),
    ) ??
    readLastTrip(trips) ??
    trips[0]?.id ??
    null;
  // 여행을 바꿔도 보던 탭을 유지한다. 탭 밖(홈·목록·도구)에서는 보드로 들어간다.
  const currentTab = tripTabOf(pathname) ?? "board";
  // 필터 없는 목록이라 키가 `/links` 화면의 기본 목록과 같다 — 사이드바를 지나온
  // 사용자는 목록이 즉시 그려진다.
  const { data: linkData } = useLinks({ enabled: link });
  const { data: linkTags } = useShortlinkTags({ enabled: link });
  // 미납은 사이드바에서도 계속 보인다 — 확정하거나 건너뛰어야만 사라진다(확정 명세 §6.4).
  const { data: ledgerSummary } = useLedgerSummary(ledger);
  const overdueCount = ledgerSummary?.overdueCount ?? 0;

  const current: WorkspaceKey = travel
    ? "travel"
    : link
      ? "link"
      : ledger
        ? "ledger"
        : "daily";

  const navItems = travel
    ? TRAVEL_TOP_ITEMS
    : link
      ? LINK_NAV_ITEMS
      : ledger
        ? LEDGER_NAV_ITEMS
        : DAILY_NAV_ITEMS;

  /**
   * 여행으로 전환 — 진행 중 여행이 있으면 곧바로 그 보드로 들어간다.
   *
   * <p>일상에 있는 동안은 요약 쿼리가 꺼져 있어 캐시를 직접 읽는다. 로그인하면 반드시
   * `/select`를 거치므로 대개 이미 채워져 있고, 없으면 여행 홈으로 보낸다.
   */
  const goToTravel = () => {
    const cached = queryClient.getQueryData<TravelSummary>(travelKeys.summary);
    navigate(cached?.ongoing?.boardPath ?? "/travel");
    onClose();
  };

  const goToDaily = () => {
    navigate("/home");
    onClose();
  };

  const goToLinks = () => {
    navigate("/links");
    onClose();
  };

  const goToLedger = () => {
    navigate("/ledger");
    onClose();
  };

  const goToSelect = () => {
    navigate("/select");
    onClose();
  };

  const CurrentIcon = WORKSPACE_ICONS[current];

  return (
    <>
      {open && (
        <button
          type="button"
          aria-label="사이드바 닫기"
          className="bg-foreground/40 fixed inset-0 z-40 md:hidden"
          onClick={onClose}
        />
      )}
      <nav
        aria-label="주 메뉴"
        className={cn(
          "border-border bg-background w-56 shrink-0 border-r",
          "fixed inset-y-0 left-0 z-50 transition-transform",
          "md:static md:translate-x-0",
          open ? "translate-x-0" : "-translate-x-full md:translate-x-0",
        )}
      >
        <div className="p-2 pb-0">
          {/*
            세그먼트가 아니라 드롭다운이다 — 224px 폭에 4칸을 넣으면 아이콘과 라벨이 눌린다
            (3칸에서 이미 gap을 6→5px로 좁혔다). 워크스페이스가 더 늘어도 트리거 폭은 그대로다.
          */}
          <Menu
            align="start"
            // 사이드바 w-56(224px)에서 좌우 패딩 p-2를 뺀 값 — 트리거와 폭이 정확히 맞는다.
            popupClassName="w-52"
            trigger={
              <button
                type="button"
                aria-label={`워크스페이스 전환 — 현재 ${WORKSPACE_LABELS[current]}`}
                className="bg-muted flex h-9 w-full items-center gap-2 rounded-lg pr-2 pl-2.5 text-[13px] font-medium"
              >
                <CurrentIcon className="size-3.5 shrink-0" />
                <span className="flex-1 text-left">
                  {WORKSPACE_LABELS[current]}
                </span>
                <ChevronsUpDown className="text-muted-foreground size-3.5 shrink-0" />
              </button>
            }
          >
            <WorkspaceMenuItem
              workspace="travel"
              current={current}
              onClick={goToTravel}
            />
            <WorkspaceMenuItem
              workspace="daily"
              current={current}
              onClick={goToDaily}
            />
            <WorkspaceMenuItem
              workspace="link"
              current={current}
              onClick={goToLinks}
            />
            <WorkspaceMenuItem
              workspace="ledger"
              current={current}
              onClick={goToLedger}
            />
            <MenuSeparator />
            <MenuItem onClick={goToSelect}>
              <LayoutGrid className="size-3.5 shrink-0 opacity-70" />
              선택 화면으로
            </MenuItem>
          </Menu>
        </div>
        <ul className="flex flex-col gap-0.5 p-2">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isReviewItem = item.to === "/planner/reviews";
            const isUpcomingItem = item.to === "/ledger/upcoming";
            // 링크 메뉴의 우측 숫자. 아직 못 받았으면 자리를 비운다 — `0`은 "링크가 없다"는
            // 뜻이고, 모르는 것과 다르다.
            const count =
              item.label === "링크 목록"
                ? linkData?.counts.all
                : item.label === "즐겨찾기"
                  ? linkData?.favorites.length
                  : undefined;
            return (
              <li key={item.label}>
                <NavLink
                  to={item.to}
                  end={EXACT_MATCH_PATHS.includes(item.to)}
                  className={({ isActive }) => {
                    const active = item.matchLocation
                      ? item.matchLocation(pathname, search)
                      : item.matchPath
                        ? item.matchPath(pathname)
                        : item.activePaths
                          ? matchesActivePaths(pathname, item.activePaths)
                          : isActive;
                    return cn(
                      "flex h-9 items-center justify-between rounded-md px-3 text-sm font-medium transition-colors",
                      active
                        ? "bg-primary/10 text-primary"
                        : "text-foreground/70 hover:bg-muted hover:text-foreground",
                    );
                  }}
                >
                  <span className="flex items-center gap-2">
                    <Icon className="size-4" />
                    {item.label}
                  </span>
                  {/*
                    미납 배지에 dismiss가 없는 것과 같은 이유로 여기서도 끄지 못한다.
                    눈에 거슬리는 게 목적이다.
                  */}
                  {isUpcomingItem && overdueCount > 0 && (
                    <span
                      aria-label={`미납 ${overdueCount}건`}
                      className="bg-destructive inline-flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-xs font-semibold text-white"
                    >
                      {overdueCount}
                    </span>
                  )}
                  {isReviewItem && reviewCount > 0 && (
                    <span
                      aria-label={`미완료 ${reviewCount}건`}
                      className="bg-primary text-primary-foreground inline-flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-xs font-semibold"
                    >
                      {reviewCount}
                    </span>
                  )}
                  {count !== undefined && (
                    <span className="text-xs tabular-nums opacity-70">
                      {count}
                    </span>
                  )}
                </NavLink>
              </li>
            );
          })}
        </ul>
        {travel && (
          <TravelTripTree
            trips={trips}
            loaded={travelData !== undefined}
            completedCount={travelData?.completedCount ?? 0}
            selectedTripId={selectedTripId}
            currentTab={currentTab}
            pathname={pathname}
            bottomItems={TRAVEL_BOTTOM_ITEMS}
          />
        )}
        {/* 태그 섹션 — 링크 워크스페이스에만 있다. 태그가 하나도 없으면 헤더도 그리지 않는다. */}
        {link && linkTags && linkTags.length > 0 && (
          <>
            <p className="text-caption text-muted-foreground mx-3 mt-3 mb-1.5 font-semibold">
              태그
            </p>
            <ul className="flex flex-col gap-0.5 px-2 pb-2">
              {linkTags.map((tag) => (
                <li key={tag.name}>
                  <NavLink
                    to={`/links?tag=${encodeURIComponent(tag.name)}`}
                    className={cn(
                      "flex h-8 items-center justify-between rounded-md px-3 text-[13px] transition-colors",
                      new URLSearchParams(search).get("tag") === tag.name
                        ? "bg-primary/10 text-primary"
                        : "text-foreground/70 hover:bg-muted hover:text-foreground",
                    )}
                  >
                    <span className="flex min-w-0 items-center gap-2">
                      <Tag className="size-3.5 shrink-0 opacity-70" />
                      <span className="truncate">{tag.name}</span>
                    </span>
                    <span className="text-xs tabular-nums opacity-70">
                      {tag.count}
                    </span>
                  </NavLink>
                </li>
              ))}
            </ul>
          </>
        )}
      </nav>
    </>
  );
}

interface TravelTripTreeProps {
  trips: SidebarTripSummary[];
  /** 요약을 받았는가. 받기 전에는 폴백 줄을 그리지 않는다 — 아래 주석 참고. */
  loaded: boolean;
  completedCount: number;
  selectedTripId: number | null;
  currentTab: TripTab;
  pathname: string;
  bottomItems: NavItem[];
}

/**
 * 여행을 사이드바에 펼친다(화면 §10.8 · 프레임 `3a`).
 *
 * <p><b>드롭다운 스위처를 만들지 않는다</b>(D-36). 선택지가 정적이고 몇 개 안 되면 접는
 * 것보다 펼치는 편이 잘 읽히고, 사이드바에 안 보이는 컨텍스트 내비게이션은 기억되지 않는다.
 * 숨김은 화면의 잡동사니를 줄이는 대신 머릿속의 잡동사니를 늘린다.
 *
 * <p>다만 진행 중·예정이 {@link TREE_DROPDOWN_THRESHOLD}개를 넘으면 여행 줄만 드롭다운으로
 * 접는다(`3b`) — 트리가 사이드바를 통째로 차지하는 것보다는 낫다.
 *
 * <p>선택된 여행 <b>하나만</b> 자식을 편다. 다른 여행은 한 줄로 접혀 사이드바가 늘 같은
 * 모양을 유지한다 — 여행을 바꿔도 눈이 항목을 다시 찾지 않아도 된다.
 */
function TravelTripTree({
  trips,
  loaded,
  completedCount,
  selectedTripId,
  currentTab,
  pathname,
  bottomItems,
}: TravelTripTreeProps) {
  const collapsed = trips.length > TREE_DROPDOWN_THRESHOLD;
  const selected = trips.find((trip) => trip.id === selectedTripId) ?? null;

  return (
    <>
      {/* 링크 워크스페이스의 「태그」 라벨과 같은 자리·같은 스타일이다. */}
      <p className="text-caption text-muted-foreground mx-3 mt-2 mb-1.5 font-semibold">
        여행
      </p>
      <ul className="flex flex-col gap-0.5 px-2">
        {collapsed ? (
          <li>
            <Menu
              align="start"
              popupClassName="w-52"
              trigger={
                <button
                  type="button"
                  aria-label={`여행 전환 — 현재 ${selected?.title ?? "여행 없음"}`}
                  className="text-foreground hover:bg-muted flex h-[34px] w-full items-center justify-between gap-1.5 rounded-md px-2.5 text-[13px] font-semibold"
                >
                  <span className="truncate">
                    {selected?.title ?? "여행 고르기"}
                  </span>
                  <ChevronsUpDown className="text-muted-foreground size-3.5 shrink-0" />
                </button>
              }
            >
              {trips.map((trip) => (
                <MenuItem
                  key={trip.id}
                  className="text-[13px]"
                  aria-current={trip.id === selectedTripId ? "true" : undefined}
                  render={<NavLink to={tripTabPath(trip.id, currentTab)} />}
                >
                  <span className="flex-1 truncate">{trip.title}</span>
                  <span className="text-caption text-muted-foreground tabular-nums">
                    {tripBadgeOf(trip)}
                  </span>
                </MenuItem>
              ))}
            </Menu>
          </li>
        ) : (
          trips.map((trip) => (
            <Fragment key={trip.id}>
              <TripTreeRow
                trip={trip}
                selected={trip.id === selectedTripId}
                currentTab={currentTab}
              />
              {/* 선택된 여행 하나만 편다 — 나머지는 한 줄로 접혀 모양이 유지된다. */}
              {trip.id === selectedTripId && (
                <TripTreeChildren trip={trip} pathname={pathname} />
              )}
            </Fragment>
          ))
        )}
        {/*
          접힌 트리에서도 선택된 여행의 자식은 그대로 편다 — 드롭다운으로 바뀌는 것은
          여행을 고르는 줄뿐이고, 보드·준비·경비까지 숨기면 탭이 사라진다.
        */}
        {collapsed && selected && (
          <TripTreeChildren trip={selected} pathname={pathname} />
        )}
        {/*
          여행을 못 정했을 때도 준비·경비로 들어갈 문은 남긴다 — 여행 목록으로 보내는 대신
          폴백 화면이 「어느 여행인가요?」를 묻는다(#1337에서 넣은 동작을 바꾼다 · D-38).
          일정 보드는 여기 없다. 보드는 여행 없이 열 수 없고 고를 것도 없다.

          요약을 받은 뒤에만 그린다. 받기 전에도 그리면 여행이 있는 사람의 사이드바에
          매번 이 두 줄이 깜빡였다가 트리로 바뀐다 — 눈이 항목을 두 번 찾게 된다.
        */}
        {loaded &&
          selectedTripId === null &&
          TRIP_TABS.filter((tab) => tab.key !== "board").map((tab) => {
            const Icon = tab.icon;
            return (
              <li key={tab.key}>
                <NavLink
                  to={`/travel/${tab.key}`}
                  className={({ isActive }) =>
                    cn(
                      "flex h-8 items-center gap-2 rounded-md px-2.5 text-[13px] font-medium transition-colors",
                      isActive
                        ? "bg-primary/10 text-primary"
                        : "text-foreground/70 hover:bg-muted hover:text-foreground",
                    )
                  }
                >
                  <Icon className="size-[15px]" />
                  {tab.label}
                </NavLink>
              </li>
            );
          })}
        <li>
          <NavLink
            to="/travel/trips/new"
            className="text-muted-foreground hover:bg-muted hover:text-foreground flex h-8 items-center gap-2 rounded-md px-3 text-[13px] transition-colors"
          >
            <Plus className="size-3.5 shrink-0" />
            여행 만들기
          </NavLink>
        </li>
        {/*
          다녀온 여행은 줄로 늘어놓지 않고 한 줄로 접는다 — 늘어놓으면 시간이 갈수록
          사이드바가 목록 화면이 된다(D-39). 목록 화면은 전부 보여 준다.
        */}
        {completedCount > 0 && (
          <li>
            <NavLink
              to="/travel/trips"
              className="text-muted-foreground hover:bg-muted hover:text-foreground flex h-8 items-center gap-2 rounded-md px-3 text-[13px] transition-colors"
            >
              <Plane className="size-3.5 shrink-0" />
              다녀온 여행 {completedCount}개
            </NavLink>
          </li>
        )}
      </ul>
      <div className="border-border mx-2 my-2 border-t" />
      <ul className="flex flex-col gap-0.5 px-2 pb-2">
        {bottomItems.map((item) => {
          const Icon = item.icon;
          return (
            <li key={item.label}>
              <NavLink
                to={item.to}
                className={({ isActive }) =>
                  cn(
                    "flex h-9 items-center rounded-md px-3 text-sm font-medium transition-colors",
                    isActive
                      ? "bg-primary/10 text-primary"
                      : "text-foreground/70 hover:bg-muted hover:text-foreground",
                  )
                }
              >
                <span className="flex items-center gap-2">
                  <Icon className="size-4" />
                  {item.label}
                </span>
              </NavLink>
            </li>
          );
        })}
      </ul>
    </>
  );
}

/** `/travel/trips/3/prep` — 여행을 바꿔도 보던 탭 그대로다. */
function tripTabPath(tripId: number, tab: TripTab): string {
  return `/travel/trips/${tripId}/${tab}`;
}

interface TripTreeRowProps {
  trip: SidebarTripSummary;
  selected: boolean;
  currentTab: TripTab;
}

/**
 * 여행 한 줄. <b>누르면 보던 탭을 유지한 채</b> 그 여행으로 간다 — 두 여행의 준비를
 * 오가는 것이 클릭 한 번이다.
 */
function TripTreeRow({ trip, selected, currentTab }: TripTreeRowProps) {
  const Chevron = selected ? ChevronDown : ChevronRight;
  const badge = tripBadgeOf(trip);
  return (
    <li>
      <NavLink
        to={tripTabPath(trip.id, currentTab)}
        aria-expanded={selected}
        className={cn(
          "flex h-[34px] w-full items-center justify-between gap-1.5 rounded-md px-2.5 text-[13px] transition-colors",
          selected
            ? "bg-muted text-foreground font-semibold"
            : "text-foreground/70 hover:bg-muted",
        )}
      >
        <span className="flex min-w-0 items-center gap-1.5">
          <Chevron className="size-3.5 shrink-0 opacity-70" />
          <span className="truncate">{trip.title}</span>
        </span>
        {badge && (
          <span
            className={cn(
              "text-caption shrink-0 tabular-nums",
              selected ? "text-primary font-semibold" : "text-muted-foreground",
            )}
          >
            {badge}
          </span>
        )}
      </NavLink>
    </li>
  );
}

interface TripTreeChildrenProps {
  trip: SidebarTripSummary;
  pathname: string;
}

/**
 * 선택된 여행의 탭 셋. 활성 판정은 <b>그 여행의 경로인지까지</b> 본다 — 경로만 보고
 * 판정하면 다른 여행의 준비를 보고 있을 때도 이 여행의 「준비」가 켜진다.
 */
function TripTreeChildren({ trip, pathname }: TripTreeChildrenProps) {
  return (
    <li>
      <ul className="border-border ml-3 flex flex-col gap-0.5 border-l pl-3.5">
        {TRIP_TABS.map((tab) => {
          const Icon = tab.icon;
          const active =
            tripTabOf(pathname) === tab.key &&
            (tripIdOf(pathname) ?? trip.id) === trip.id;
          return (
            <li key={tab.key}>
              <NavLink
                to={tripTabPath(trip.id, tab.key)}
                className={cn(
                  "flex h-8 items-center justify-between rounded-md px-2.5 text-[13px] font-medium transition-colors",
                  active
                    ? "bg-primary/10 text-primary"
                    : "text-foreground/70 hover:bg-muted hover:text-foreground",
                )}
              >
                <span className="flex items-center gap-2">
                  <Icon className="size-[15px]" />
                  {tab.label}
                </span>
                {/*
                  준비 배지는 「무시」가 없다(§13). 체크하거나 기한을 옮겨야 사라진다 —
                  끌 수 있는 경고는 곧 아무도 안 보는 경고가 된다.
                */}
                {tab.key === "prep" && trip.prep.overdueCount > 0 && (
                  <span
                    aria-label={`기한 지난 것 ${trip.prep.overdueCount}개`}
                    className="bg-destructive inline-flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-xs font-semibold text-white"
                  >
                    {trip.prep.overdueCount}
                  </span>
                )}
              </NavLink>
            </li>
          );
        })}
      </ul>
    </li>
  );
}

interface WorkspaceMenuItemProps {
  workspace: WorkspaceKey;
  current: WorkspaceKey;
  onClick: () => void;
}

/** 스위처 항목. 지금 있는 곳에는 `Check`를 둔다 — 열었을 때 어디인지가 먼저 보여야 한다. */
function WorkspaceMenuItem({
  workspace,
  current,
  onClick,
}: WorkspaceMenuItemProps) {
  const Icon = WORKSPACE_ICONS[workspace];
  const active = workspace === current;
  return (
    <MenuItem
      aria-current={active ? "true" : undefined}
      onClick={onClick}
      className="text-[13px]"
    >
      <Icon className="size-3.5 shrink-0" />
      <span className="flex-1">{WORKSPACE_LABELS[workspace]}</span>
      {active && <Check className="text-primary size-3.5 shrink-0" />}
    </MenuItem>
  );
}
