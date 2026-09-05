import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";

/**
 * 「여행 › 일본 가을 › 준비」 — <b>화면이 스스로 어느 여행인지 말한다</b>(화면 §10.8).
 *
 * <p>모바일에서는 사이드바가 닫혀 있다. 사이드바만 여행 이름을 들고 있으면, 폭이 좁은
 * 화면에서는 「준비 / 출발까지 49일」만 남아 <b>어느 여행의 준비인지가 화면 어디에도 없다</b>.
 *
 * <p>여행 이름은 <b>그 여행의 보드</b>로 간다 — 이름을 눌렀을 때 가고 싶은 곳은 여행 목록이
 * 아니라 그 여행이다. 「여행」만 목록으로 보낸다.
 *
 * <p>이름을 아직 못 받았으면 그 칸을 통째로 뺀다. 자리만 잡아 두면 빈 칸에 구분자 두 개가
 * 붙어 「여행 › › 준비」가 된다.
 */
export function TripBreadcrumb({
  tripId,
  tripTitle,
  current,
}: {
  tripId: number;
  /** 여행 이름. 아직 못 받았으면 생략한다. */
  tripTitle?: string;
  /** 지금 화면 이름 — 「준비」·「경비」. 링크가 아니다(이미 여기 있다). */
  current: string;
}) {
  return (
    <nav
      aria-label="현재 위치"
      // 좁은 화면에서 줄바꿈 대신 이름을 자른다 — 두 줄이 되면 헤더가 통째로 밀린다.
      className="text-muted-foreground flex min-w-0 items-center gap-0.5 text-[13px]"
    >
      <Link to="/travel/trips" className="hover:text-foreground shrink-0">
        여행
      </Link>
      {tripTitle && (
        <>
          <ChevronRight className="size-[13px] shrink-0 opacity-60" />
          <Link
            to={`/travel/trips/${tripId}/board`}
            className="hover:text-foreground truncate"
          >
            {tripTitle}
          </Link>
        </>
      )}
      <ChevronRight className="size-[13px] shrink-0 opacity-60" />
      <span className="text-foreground shrink-0">{current}</span>
    </nav>
  );
}
