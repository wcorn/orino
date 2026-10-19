import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { act } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { toast, toastUndo, useToastStore } from "@/shared/lib/toast";

import { Toaster } from "./Toaster";

describe("Toaster", () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("일반 알림은 메시지와 닫기 버튼만 보여준다", async () => {
    render(<Toaster />);

    act(() => {
      toast("저장했어요", "success");
    });

    expect(await screen.findByText("저장했어요")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "알림 닫기" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /실행취소/ })).toBeNull();
  });

  it("실행취소 스낵바는 액션 버튼과 남은 초를 보여준다", async () => {
    render(<Toaster />);

    act(() => {
      toastUndo("일정을 삭제했어요", { onUndo: () => {} });
    });

    const undo = await screen.findByRole("button", { name: /실행취소/ });
    // 5초 카운트다운. 렌더 시점에 따라 5 또는 4로 보일 수 있다.
    expect(undo.textContent).toMatch(/실행취소\s*[45]/);
  });

  it("실행취소를 누르면 onUndo가 실행되고 스낵바가 닫힌다", async () => {
    const onUndo = vi.fn();
    render(<Toaster />);

    act(() => {
      toastUndo("지웠어요", { onUndo });
    });

    await userEvent.click(
      await screen.findByRole("button", { name: /실행취소/ }),
    );

    expect(onUndo).toHaveBeenCalledTimes(1);
    await waitFor(() => {
      expect(screen.queryByText("지웠어요")).toBeNull();
    });
  });

  it("그냥 두면 시간이 다 된 뒤 닫히기만 한다 — 그때 실행되는 일은 없다", async () => {
    // 닫힐 때 요청을 보내던 쓰임(5초 보류)은 없앴다(#1432).
    vi.useFakeTimers();
    const onUndo = vi.fn();
    render(<Toaster />);

    act(() => {
      toastUndo("지웠어요", { onUndo, durationMs: 5000 });
    });
    expect(screen.getByText("지웠어요")).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(onUndo).not.toHaveBeenCalled();
    expect(screen.queryByText("지웠어요")).toBeNull();
  });

  it("닫기 버튼으로 닫으면 onUndo가 실행되지 않는다", async () => {
    const onUndo = vi.fn();
    render(<Toaster />);

    act(() => {
      toastUndo("지웠어요", { onUndo });
    });

    await userEvent.click(
      await screen.findByRole("button", { name: "알림 닫기" }),
    );

    expect(onUndo).not.toHaveBeenCalled();
  });

  it("액션 없는 알림은 카운트다운을 그리지 않는다", async () => {
    render(<Toaster />);

    act(() => {
      toast("저장했어요");
    });

    const row = await screen.findByRole("status");
    expect(row.textContent).toBe("저장했어요");
  });

  it("여러 개가 쌓여도 각자 자기 액션을 실행한다", async () => {
    const first = vi.fn();
    const second = vi.fn();
    render(<Toaster />);

    act(() => {
      toastUndo("첫 번째", { onUndo: first });
      toastUndo("두 번째", { onUndo: second });
    });

    const buttons = await screen.findAllByRole("button", { name: /실행취소/ });
    expect(buttons).toHaveLength(2);

    await userEvent.click(buttons[1]);

    expect(second).toHaveBeenCalledTimes(1);
    expect(first).not.toHaveBeenCalled();
    expect(screen.getByText("첫 번째")).toBeInTheDocument();
  });
});
