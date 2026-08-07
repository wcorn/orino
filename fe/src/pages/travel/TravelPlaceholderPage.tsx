import { PageHeader } from "@/components/PageHeader";

interface TravelPlaceholderPageProps {
  title: string;
}

/**
 * 여행 워크스페이스 라우트 자리를 잡아두는 임시 페이지.
 *
 * <p>이번 작업(#1033)은 워크스페이스 분리와 이동 경로까지다. 각 화면은 후속 이슈에서
 * 이 자리를 그대로 대체한다 — 여행 홈·목록(#1035), 생성·수정(#1036), 일정 보드(#1037),
 * 일정 상세(#1039), 도구·설정은 3·4단계.
 *
 * <p>화면을 미리 그리지 않고 제목만 두는 이유는, 지금 만든 UI가 후속 이슈에서 어차피
 * 버려지기 때문이다.
 */
export function TravelPlaceholderPage({ title }: TravelPlaceholderPageProps) {
  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-6">
      <PageHeader title={title} description="곧 이 자리에 화면이 들어옵니다." />
    </div>
  );
}
