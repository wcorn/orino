import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

function link(overrides: Record<string, unknown> = {}) {
  return {
    slug: "jeju",
    shortUrl: "https://s.orino.dev/jeju",
    targetUrl: "https://img.orino.dev/note-images/2026/aug.jpg",
    memo: null,
    tags: [],
    custom: true,
    favorite: false,
    state: "ACTIVE",
    hasPassword: false,
    visitCount: 0,
    lastVisitedAt: null,
    ...overrides,
  };
}

function mockList(data: Record<string, unknown>) {
  server.use(
    http.get(`${API_BASE}/shortlinks`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          counts: { all: 0, active: 0, inactive: 0 },
          favorites: [],
          recent: [],
          ...data,
        },
      }),
    ),
  );
}

function renderLinks(path = "/links") {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: [path] },
  );
}

/**
 * 링크 목록 · 빠른 발급(#1239).
 *
 * <p>이 테스트가 지키는 것 중 첫째는 <b>클립보드에 무엇이 들어가는가</b>다. 목록에 낙관적으로
 * 한 줄 먼저 넣는 것은 되돌리면 그만이지만, 클립보드에 잘못된 주소가 들어가면 사용자는 그것을
 * 남에게 보낸 뒤에야 알게 된다.
 */
describe("LinkListPage", () => {
  const writeText = vi.fn().mockResolvedValue(undefined);

  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    writeText.mockClear();
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText },
      configurable: true,
    });
  });

  it("붙여넣고 Enter → 목록 맨 위에 행이 생기고 서버가 준 슬러그가 클립보드에 들어간다", async () => {
    server.use(
      // 응답을 살짝 늦춰 낙관적 행이 보이는 구간을 만든다.
      http.post(`${API_BASE}/shortlinks`, async () => {
        await delay(50);
        return HttpResponse.json({
          code: "OK",
          data: {
            ...link({ slug: "9dwqr", shortUrl: "https://s.orino.dev/9dwqr" }),
            qrPayload: "https://s.orino.dev/9dwqr",
          },
        });
      }),
    );

    renderLinks();
    const input = await screen.findByLabelText("빠른 발급 URL");
    await userEvent.type(input, "https://example.com/photo.jpg{Enter}");

    // 응답 전에 이미 목록에 보인다 — 낙관적 삽입.
    expect(
      await screen.findByText("https://example.com/photo.jpg"),
    ).toBeInTheDocument();

    await waitFor(() => {
      // 낙관적으로 만들어 낸 값이 아니라 서버 응답으로만 복사한다.
      expect(writeText).toHaveBeenCalledWith("https://s.orino.dev/9dwqr");
    });
    expect(writeText).toHaveBeenCalledTimes(1);
  });

  it("발급이 실패하면 낙관적 행이 사라진다", async () => {
    server.use(
      http.post(`${API_BASE}/shortlinks`, async () => {
        await delay(50);
        return HttpResponse.json(
          { code: "SL-ERR-001", message: "지원하지 않는 주소 형식입니다." },
          { status: 400 },
        );
      }),
    );

    renderLinks();
    const input = await screen.findByLabelText("빠른 발급 URL");
    await userEvent.type(input, "javascript:alert(1){Enter}");

    // 넣었다가
    expect(await screen.findByText("javascript:alert(1)")).toBeInTheDocument();
    // 실패하면 걷어낸다.
    await waitFor(() => {
      expect(screen.queryByText("javascript:alert(1)")).toBeNull();
    });
    // 없는 링크가 목록에 남아 있으면 다음에 눌러 보고 404다.
    expect(writeText).not.toHaveBeenCalled();
  });

  it("커스텀 슬러그가 이미 쓰이면 필드 오류를 보여주고 제출을 막는다", async () => {
    server.use(
      http.get(`${API_BASE}/shortlinks/slug-available`, () =>
        HttpResponse.json({ code: "OK", data: { available: false } }),
      ),
    );

    renderLinks();
    await userEvent.click(
      await screen.findByRole("button", { name: /새 링크/ }),
    );
    await userEvent.type(
      await screen.findByLabelText(/목적지 URL/),
      "https://example.com",
    );
    await userEvent.type(screen.getByLabelText(/커스텀 슬러그/), "jeju");

    expect(await screen.findByText("이미 사용 중이에요")).toBeInTheDocument();
    // 빠른 발급 바에도 같은 이름의 버튼이 있다 — 모달 안에서 찾는다.
    const modal = within(screen.getByRole("dialog"));
    expect(modal.getByRole("button", { name: "만들기" })).toBeDisabled();
  });

  it("프리뷰를 못 읽어도 발급은 막히지 않는다", async () => {
    server.use(
      http.get(`${API_BASE}/shortlinks/og-preview`, async () => {
        // 느리게 답한다 — 프리뷰를 기다리느라 제출이 막히면 여기서 드러난다.
        await delay(300);
        return HttpResponse.json({
          code: "OK",
          data: { ok: false, title: null, imageUrl: null },
        });
      }),
      http.post(`${API_BASE}/shortlinks`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            ...link({ slug: "9dwqr" }),
            qrPayload: "https://s.orino.dev/9dwqr",
          },
        }),
      ),
    );

    renderLinks();
    await userEvent.click(
      await screen.findByRole("button", { name: /새 링크/ }),
    );
    const modal = within(screen.getByRole("dialog"));
    await userEvent.type(
      modal.getByLabelText(/목적지 URL/),
      "https://example.com/photo.jpg",
    );

    // 프리뷰 자리는 비어 있어도 만들기는 눌린다.
    expect(modal.getByText(/프리뷰는 확인용이에요/)).toBeInTheDocument();
    expect(modal.getByRole("button", { name: "만들기" })).toBeEnabled();

    await userEvent.click(modal.getByRole("button", { name: "만들기" }));
    await waitFor(() => {
      expect(screen.getByText("클립보드에 복사했어요")).toBeInTheDocument();
    });
  });

  it("복사 버튼은 복사만 한다 — 행 이동을 일으키지 않는다", async () => {
    mockList({ counts: { all: 1, active: 1, inactive: 0 }, recent: [link()] });

    renderLinks();
    await userEvent.click(
      await screen.findByRole("button", { name: "주소 복사" }),
    );

    await waitFor(() => {
      expect(writeText).toHaveBeenCalledWith("https://s.orino.dev/jeju");
    });
    // 상세로 갔다면 목록 제목이 사라진다.
    expect(screen.getByRole("heading", { name: "링크" })).toBeInTheDocument();
  });

  it("비활성 행에는 QR 버튼이 없다 — 열리지 않는 주소의 QR은 종이 낭비다", async () => {
    mockList({
      counts: { all: 2, active: 1, inactive: 1 },
      recent: [
        link({
          slug: "busan",
          shortUrl: "https://s.orino.dev/busan",
          state: "DISABLED",
        }),
        link(),
      ],
    });

    renderLinks();
    const rows = await screen.findAllByRole("button", {
      name: /s\.orino\.dev/,
    });
    const disabledRow = rows.find((row) => within(row).queryByText("꺼짐"));

    expect(disabledRow).toBeDefined();
    expect(
      within(disabledRow!).queryByRole("button", { name: "QR 보기" }),
    ).toBeNull();
    const activeRow = rows.find((row) => !within(row).queryByText("꺼짐"));
    expect(
      within(activeRow!).getByRole("button", { name: "QR 보기" }),
    ).toBeInTheDocument();
  });

  it("링크가 하나도 없으면 빈 상태를 보여준다", async () => {
    renderLinks();
    expect(
      await screen.findByText("아직 만든 링크가 없어요."),
    ).toBeInTheDocument();
  });

  it("즐겨찾기 필터에서는 최근 발급 섹션을 접는다", async () => {
    mockList({
      counts: { all: 2, active: 2, inactive: 0 },
      favorites: [link({ favorite: true })],
      recent: [link({ slug: "busan", shortUrl: "https://s.orino.dev/busan" })],
    });

    renderLinks("/links?favorite=1");

    expect(await screen.findByText("즐겨찾기")).toBeInTheDocument();
    expect(screen.queryByText("최근 발급")).toBeNull();
    expect(screen.queryByText(/busan/)).toBeNull();
  });
});
