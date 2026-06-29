# design-sync NOTES (orino-fe)

이 리포는 **퍼블리시되는 라이브러리가 아니라 Vite 앱**이다. 디자인 시스템은 `src/components/ui` + `src/components`(앱 레벨)에 있고, 토큰은 `src/index.css`(Tailwind v4 `@theme`)에 있다.

## 빌드/엔트리 (synth-entry)

- 라이브러리 dist 엔트리가 없어 **synth-entry 모드**로 `src/components`에서 직접 번들한다(`cfg.srcDir = "src/components"`).
- 컨버터는 `node_modules/orino-fe/package.json`을 읽으려 한다(앱은 self-install 안 됨). 해결: `node_modules/orino-fe`를 리포로 심링크.
  - **주의**: 이 워크트리의 `node_modules`는 메인 리포(`/Users/dongseok/Documents/GitHub/orino/fe/node_modules`)로의 심링크다. 그래서 `ln -sfn .. node_modules/orino-fe`는 메인 리포 fe를 가리켜 버린다. 반드시 **절대경로**로:
    `ln -sfn /Users/dongseok/Documents/GitHub/orino/.claude/worktrees/be-fe/fe node_modules/orino-fe`
- `@/*` 별칭은 esbuild가 자동 tsconfig 탐지로 해석한다(cfg.tsconfig는 "outside workspace root"로 skip되지만 동작에는 무관).

## 토큰/폰트 (Tailwind v4)

- 컴포넌트는 Tailwind 유틸리티로 스타일링되므로 **컴파일된 CSS**가 필요하다. `cfg.cssEntry`는 `npm run build` 산출물 `dist/assets/index-*.css`(컴파일된 Tailwind, 토큰 :root + 유틸 포함).
- Pretendard는 `cfg.extraFonts`로 `dist/assets/PretendardVariable-*.woff2`를 싣는다.

## Known render warns (정상 — 새 경고 아님)

- `[FONT_MISSING]` "Pretendard"(정적), "Apple SD Gothic Neo"(시스템) — 폴백 패밀리. **Pretendard Variable는 실린다**. 대체 폴백 허용.
- thin(2): `Menu`(닫힌 트리거 상태 — 여는 건 인터랙션), 짧은 단일-룩 컴포넌트. 정상.

## 플로어 카드(의도적 — 정적 프리뷰 부적합)

- `DialogPopup`, `DialogFormFooter`, `MenuItem`, `Toaster` — 포털/Dialog 컨텍스트/스토어 상태에 의존해 정적 카드로 의미있게 렌더 불가. 기능 번들에는 모두 포함됨(import 가능). 추후 부모 조합으로 작성 가능.

## Re-sync 위험(다음 실행이 주의할 것)

- **해시 경로 취약**: `cfg.cssEntry`와 `cfg.extraFonts`가 content-hash된 파일명(`index-CUGrMVGd.css`, `PretendardVariable-CJuje-Rk.woff2`)을 가리킨다. `npm run build` 때마다 해시가 바뀌므로, 재동기화 시 **먼저 `npm run build` → config의 두 해시 경로를 새 파일명으로 갱신**해야 한다.
- **심링크 재생성 필요**: `node_modules/orino-fe` 심링크는 gitignore(node_modules)라 클론마다 위 절대경로로 다시 만들어야 한다.
- **워크트리/메인 node_modules 공유**: 위 "주의" 참고.
