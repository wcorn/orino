import { cn } from "@/lib/utils";

interface ShortUrlTextProps {
  /** 서버가 준 짧은 주소 전체(`https://s.orino.dev/ab3k9`). */
  shortUrl: string;
  /** 슬러그. 낙관적 행처럼 주소가 아직 없을 때도 이 값은 있다. */
  slug: string;
  className?: string;
  slugClassName?: string;
}

/**
 * 짧은 주소를 도메인과 슬러그로 나눠 보여 준다.
 *
 * <p><b>슬러그만 진하게 두는 것은 장식이 아니다.</b> 목록에서 사람이 실제로 구분해 읽는
 * 부분이 슬러그 5자뿐이고, 앞의 도메인 11자는 모든 행에서 똑같다(화면 설계 §3.3).
 *
 * <p>주소 조립은 서버가 한다. 여기서는 <b>받은 문자열을 자르기만</b> 한다 —
 * 프론트가 도메인을 알기 시작하면 환경이 달라질 때 두 곳이 어긋난다.
 */
export function ShortUrlText({
  shortUrl,
  slug,
  className,
  slugClassName,
}: ShortUrlTextProps) {
  // `https://` 는 지우고 도메인부터 보여 준다. 슬러그는 뒤에서 잘라 낸다.
  const withoutScheme = shortUrl.replace(/^https?:\/\//, "");
  const prefix = withoutScheme.endsWith(`/${slug}`)
    ? withoutScheme.slice(0, -slug.length)
    : "";

  return (
    <span className={cn("text-muted-foreground tracking-[-0.01em]", className)}>
      {prefix}
      <span className={cn("text-foreground font-semibold", slugClassName)}>
        {slug}
      </span>
    </span>
  );
}
