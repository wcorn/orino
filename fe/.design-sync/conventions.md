# orino Design System

React + Tailwind CSS v4 component library (shadcn/ui 스타일). 컴포넌트는 내부적으로 Tailwind 유틸리티 + 시맨틱 OKLCH 토큰으로 스타일링되어 있고, 레이아웃 글루는 같은 Tailwind 유틸리티 어휘로 작성한다.

## 셋업

- **별도 Provider/래퍼는 필요 없다.** 디자인 토큰은 `:root`(다크모드는 `.dark`)의 CSS 변수로 정의되며 `styles.css`로 실린다 — `styles.css`만 로드되면 된다.
- **다크모드**: 루트 요소에 `dark` 클래스를 추가하면 토큰 값이 전환된다.
- **폰트**: Pretendard Variable(한글 우선). 미로드 시 시스템 폰트로 폴백.
- 클래스 병합 헬퍼는 내부적으로 `cn()`(clsx + tailwind-merge)를 쓴다 — 소비 측에서는 그냥 `className`에 Tailwind 유틸리티를 넘기면 된다.

## 스타일 idiom — Tailwind v4 유틸리티 + 시맨틱 토큰

레이아웃은 DS 토큰을 참조하는 Tailwind 유틸리티 클래스로 작성한다(임의 hex 색상 금지, 토큰 사용):

- 표면: `bg-background`, `bg-card`, `bg-muted`, `bg-popover`
- 텍스트: `text-foreground`, `text-muted-foreground`, `text-primary`, `text-destructive`
- 강조: `bg-primary text-primary-foreground`, `bg-secondary`, `bg-destructive text-destructive-foreground`
- 테두리/링: `border border-border`, `ring-ring`
- 반경: `rounded-md`, `rounded-lg`(`--radius` 기반)
- 브랜드 색: 토큰 `--brand`(보라 `oklch(0.55 0.25 297.5)`) — 유틸리티가 필요하면 `text-[var(--brand)]` / `bg-[var(--brand)]`로 사용
- 간격은 표준 Tailwind 스케일(`gap-3`, `p-4`). 타이포는 시맨틱 스케일 토큰을 쓴다: `text-title`(페이지 제목 24) · `text-heading`(섹션/카드 제목 18) · `text-body`(15) · `text-label`(14) · `text-caption`(보조 12), `text-display`(32)는 히어로용. 색과 함께 쓸 때도 `cn("text-muted-foreground text-caption")`처럼 안전(폰트크기 그룹 등록됨)

## 컴포넌트 사용

- **버튼**: `<Button variant="default|secondary|outline|ghost|destructive|link" size="sm|default|lg">`
- **폼**: 컨트롤을 `<FormField label htmlFor error>`로 감싼다. 컨트롤은 `<Input>` · `<Textarea>` · `<Select value onValueChange options={[{value,label}]}>` · `<Checkbox>` · `<Switch checked onCheckedChange>`. 오류 문구는 `<FieldError>`
- **카드**: `<Card>` 안에 `<CardHeader><CardTitle/><CardDescription/><CardAction/></CardHeader>` · `<CardContent>` · `<CardFooter>`로 조합
- **오버레이**: 범용 다이얼로그는 `<Modal open onOpenChange title description? size="sm|md|lg">{본문}<Modal.Footer/></Modal>` 하나로 조립한다(제목·설명·폭은 prop, 하단 액션은 `Modal.Footer`). `<Modal.Footer submitLabel onSubmit? pending? pendingLabel? submitDisabled? destructive? onDelete?/>`(폼 안에서 `onSubmit` 없이 쓰면 form submit), 커스텀 액션은 `children`. 확인/삭제는 프리셋 `<ConfirmDialog title description confirmLabel destructive onConfirm/>`. 메뉴는 `<Menu trigger={<Button/>}><MenuItem/></Menu>`. (공개 다이얼로그 표면은 `Modal`·`ConfirmDialog` 둘뿐 — DialogPopup/DialogFooter/DialogFormFooter는 Modal에 흡수됨)
- **헤더**: 페이지 제목은 `<PageHeader title description actions/>`, 섹션 제목은 `<SectionHeader size="sm|md" level={2|3}>`
- **상태/피드백**: 빈 상태 `<EmptyState>`, 로딩 `<LoadingText>`, 인라인 알림 `<Alert variant="info|success|warning|destructive"><AlertTitle/><AlertDescription/></Alert>`(아이콘은 svg 자식), 라벨 칩 `<Badge variant="default|secondary|success|warning|info|destructive|outline">`
- **데이터/내비**: `<Table>`+`<TableHeader/TableBody/TableRow/TableHead/TableCell>`(상태 칸엔 Badge 조합), `<Tabs defaultValue><TabsList><TabsTrigger value/></TabsList><TabsContent value/></Tabs>`, `<Tooltip><TooltipTrigger/><TooltipContent/></Tooltip>`
- **상태색 규칙**: 성공/주의/정보/실패는 `success|warning|info|destructive` 시맨틱 토큰만 쓴다(임의 green/amber/red 금지). 일요일·공휴일 빨강 같은 캘린더 도메인 색은 예외

## 출처(읽어야 할 곳)

- 토큰·유틸리티 정의: 번들의 `styles.css`와 그 `@import` 클로저(`_ds_bundle.css` — 토큰 `:root` 블록 + Tailwind 레이어)
- 컴포넌트별 API·예시: 각 컴포넌트의 `.d.ts` · `.prompt.md`

## 예시

```tsx
import {
  Button,
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "orino-fe";

<Card className="max-w-sm">
  <CardHeader>
    <CardTitle>복습 카드</CardTitle>
  </CardHeader>
  <CardContent className="text-muted-foreground text-sm">
    오늘 복습할 카드 8장
  </CardContent>
  <CardFooter>
    <Button size="sm">복습 시작</Button>
  </CardFooter>
</Card>;
```
