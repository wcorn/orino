import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Editor } from "@tiptap/core";
import StarterKit from "@tiptap/starter-kit";
import { act } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { noteLinkOptions } from "../editor/link";
import { EditorToolbar } from "./EditorToolbar";

describe("EditorToolbar — 링크 버튼", () => {
  let editor: Editor;

  beforeEach(() => {
    const element = document.createElement("div");
    document.body.appendChild(element);
    editor = new Editor({
      element,
      extensions: [StarterKit.configure({ link: noteLinkOptions })],
      content: "<p>hello world</p>",
    });
  });

  afterEach(() => {
    editor.destroy();
    vi.restoreAllMocks();
  });

  it("선택이 없고 링크도 아니면 비활성", () => {
    act(() => {
      editor.chain().setTextSelection(3).run(); // collapsed caret
    });
    render(<EditorToolbar editor={editor} onInsertLink={() => {}} />);
    expect(screen.getByRole("button", { name: "링크" })).toBeDisabled();
  });

  it("텍스트가 선택되면 활성화되고, 클릭 시 onInsertLink 호출", async () => {
    const onInsertLink = vi.fn();
    act(() => {
      editor.chain().setTextSelection({ from: 1, to: 6 }).run(); // "hello"
    });
    render(<EditorToolbar editor={editor} onInsertLink={onInsertLink} />);

    const btn = screen.getByRole("button", { name: "링크" });
    expect(btn).toBeEnabled();

    await userEvent.click(btn);
    expect(onInsertLink).toHaveBeenCalledTimes(1);
  });

  it("커서가 링크 위에 있으면 눌린 상태(aria-pressed)로 표시", () => {
    act(() => {
      editor
        .chain()
        .setTextSelection({ from: 1, to: 6 })
        .setLink({ href: "https://example.com" })
        .setTextSelection(3) // 링크 안으로 커서 이동(선택 없음)
        .run();
    });
    render(<EditorToolbar editor={editor} onInsertLink={() => {}} />);

    const btn = screen.getByRole("button", { name: "링크" });
    expect(btn).toHaveAttribute("aria-pressed", "true");
    // 링크 위에서는 선택이 없어도 활성(편집 가능).
    expect(btn).toBeEnabled();
  });

  it("onInsertLink가 없으면 링크 버튼을 렌더하지 않는다", () => {
    render(<EditorToolbar editor={editor} />);
    expect(
      screen.queryByRole("button", { name: "링크" }),
    ).not.toBeInTheDocument();
  });
});
