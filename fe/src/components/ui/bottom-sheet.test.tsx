import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import { BottomSheet } from "./bottom-sheet";
import { Button } from "./button";

function Harness({ initialOpen = false }: { initialOpen?: boolean }) {
  const [open, setOpen] = useState(initialOpen);
  return (
    <>
      <Button onClick={() => setOpen(true)}>열기</Button>
      <BottomSheet
        open={open}
        onOpenChange={setOpen}
        title="어느 날짜에 담을까요"
        description="나중에 옮길 수 있어요"
      >
        <Button onClick={() => setOpen(false)}>1일차</Button>
      </BottomSheet>
    </>
  );
}

describe("BottomSheet", () => {
  it("닫혀 있으면 아무것도 렌더하지 않는다", () => {
    render(<Harness />);

    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("열면 제목·설명·본문이 보인다", async () => {
    render(<Harness />);

    await userEvent.click(screen.getByRole("button", { name: "열기" }));

    const sheet = await screen.findByRole("dialog");
    expect(sheet).toHaveTextContent("어느 날짜에 담을까요");
    expect(sheet).toHaveTextContent("나중에 옮길 수 있어요");
    expect(screen.getByRole("button", { name: "1일차" })).toBeInTheDocument();
  });

  it("ESC로 닫힌다", async () => {
    render(<Harness initialOpen />);
    await screen.findByRole("dialog");

    await userEvent.keyboard("{Escape}");

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
  });

  it("본문에서 닫으면 onOpenChange로 상태가 내려간다", async () => {
    render(<Harness initialOpen />);
    await screen.findByRole("dialog");

    await userEvent.click(screen.getByRole("button", { name: "1일차" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
  });

  it("열리면 포커스가 시트 안으로 들어간다", async () => {
    render(<Harness initialOpen />);

    const sheet = await screen.findByRole("dialog");
    // 모달 여부는 base-ui가 형제 요소에 inert를 거는 방식으로 처리한다(aria-modal 아님).
    await waitFor(() => {
      expect(sheet.contains(document.activeElement)).toBe(true);
    });
  });

  it("하단 정렬 + 아래에서 올라오는 모션 클래스를 갖는다", async () => {
    render(<Harness initialOpen />);

    const sheet = await screen.findByRole("dialog");
    expect(sheet.className).toContain("bottom-0");
    expect(sheet.className).toContain("data-[starting-style]:translate-y-full");
    expect(sheet.className).toContain("duration-[180ms]");
  });
});
