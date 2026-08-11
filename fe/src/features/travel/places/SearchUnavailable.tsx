import type { SearchFailure } from "@/features/travel/lib/searchFailure";

interface SearchUnavailableProps {
  failure: SearchFailure;
  /** 이 화면이 찾는 것. 문구가 "새 도시" / "새 장소"로 갈린다. */
  subject?: "장소" | "도시";
}

/**
 * 검색이 안 될 때의 안내.
 *
 * <p><b>거절(할당량·키)과 일반 실패를 갈라 말한다.</b> "잠시 후 다시 시도해 주세요"는 캡에
 * 걸린 사용자에게 틀린 조언이다 — 잠시 후에도 안 되고, 시도할 때마다 또 거절당한다. 반대로
 * 일시적 네트워크 오류에 "이미 담아 둔 장소는 쓸 수 있어요"라고 하면 복구를 기다릴 이유를
 * 없애 버린다.
 *
 * <p>거절 문구가 <b>할 수 있는 일</b>로 끝나는 이유는 실제로 앱의 대부분이 살아 있기
 * 때문이다 — 담아 둔 장소, 캐시된 검색어, 일정 편집은 그대로 된다.
 */
export function SearchUnavailable({
  failure,
  subject = "장소",
}: SearchUnavailableProps) {
  if (!failure) return null;
  return (
    <p className="text-muted-foreground text-sm">
      {failure === "rejected"
        ? `지금은 새 ${subject}를 검색할 수 없어요. 이미 담아 둔 장소는 그대로 쓸 수 있습니다.`
        : `${subject}를 검색하지 못했어요. 잠시 후 다시 시도해 주세요.`}
    </p>
  );
}
