import type { Editor } from "@tiptap/react";
import { ArrowDown, ArrowUp, Copy, Trash2 } from "lucide-react";
import { type ReactNode, useEffect, useState } from "react";

import { cn } from "@/lib/utils";

import {
  duplicateBlocks,
  moveBlocks,
  selectBlockAt,
  selectionCovers,
  targetBlockRange,
} from "../editor/blockActions";

interface Props {
  editor: Editor | null;
}

/** 메뉴가 뷰포트 밖으로 나가지 않게 잡아둘 대략의 크기. */
const MENU_WIDTH = 192;
const MENU_HEIGHT = 180;

/**
 * 블록 우클릭 메뉴. 키보드 단축키로만 가능하던 블록 조작(복제·이동·삭제)을 마우스에도 연다.
 *
 * 옵션은 우클릭 하나로 통일한다(#880에서 표가 내린 결정과 같은 방향 — 발견성보다 단순성·비중복).
 * 표 안에서의 우클릭은 표가 자기 메뉴를 띄우므로 여기서 가로채지 않는다.
 */
export function BlockContextMenu({ editor }: Props) {
  const [menu, setMenu] = useState<{ x: number; y: number } | null>(null);

  useEffect(() => {
    if (!editor) return;
    const dom = editor.view.dom;

    const onContextMenu = (e: MouseEvent) => {
      const target = e.target as HTMLElement | null;
      // 표는 자기 우클릭 메뉴를 갖고 있다 — 건드리지 않는다.
      if (target?.closest("[data-dataset-table]")) return;

      const at = editor.view.posAtCoords({ left: e.clientX, top: e.clientY });
      if (!at) return;

      e.preventDefault();
      // 이미 고른 블록들 위에서 누른 거면 그 선택을 유지한다(여러 블록 조작을 이어가게).
      // 바깥을 눌렀으면 그 블록으로 선택을 옮긴다.
      if (!selectionCovers(editor.state, at.pos)) {
        selectBlockAt(editor, at.pos);
      }
      setMenu({ x: e.clientX, y: e.clientY });
    };

    dom.addEventListener("contextmenu", onContextMenu);
    return () => dom.removeEventListener("contextmenu", onContextMenu);
  }, [editor]);

  // 메뉴가 열려 있는 동안의 Esc는 메뉴만 닫는다(선택 사다리를 건드리지 않게 capture에서 잡는다).
  useEffect(() => {
    if (!menu) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      e.preventDefault();
      e.stopPropagation();
      setMenu(null);
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [menu]);

  if (!editor || !menu) return null;

  const range = targetBlockRange(editor.state);
  const blockCount = range ? range.lastIndex - range.firstIndex + 1 : 0;

  const run = (action: () => void) => () => {
    action();
    setMenu(null);
    editor.commands.focus();
  };

  const item = (
    label: string,
    shortcut: string,
    icon: ReactNode,
    action: () => void,
    destructive = false,
  ) => (
    <button
      type="button"
      role="menuitem"
      onClick={run(action)}
      className={cn(
        "hover:bg-accent flex w-full items-center gap-2 rounded px-2 py-1.5 text-left text-sm",
        destructive && "text-destructive",
      )}
    >
      {icon}
      <span className="flex-1">{label}</span>
      <span className="text-muted-foreground text-xs">{shortcut}</span>
    </button>
  );

  return (
    <>
      {/* 바깥 클릭·우클릭으로 닫는다. */}
      <div
        className="fixed inset-0 z-40"
        onClick={() => setMenu(null)}
        onContextMenu={(e) => {
          e.preventDefault();
          setMenu(null);
        }}
      />
      <div
        role="menu"
        aria-label="블록 메뉴"
        className="border-border bg-popover fixed z-50 min-w-44 rounded-md border p-1 shadow-md"
        style={{
          left: Math.min(menu.x, window.innerWidth - MENU_WIDTH),
          top: Math.min(menu.y, window.innerHeight - MENU_HEIGHT),
        }}
      >
        <div className="text-muted-foreground px-2 py-1 text-xs">
          블록 {blockCount}개
        </div>
        {item("복제", "⌘D", <Copy className="size-3.5" />, () =>
          duplicateBlocks(editor),
        )}
        {item("위로 이동", "⌘⇧↑", <ArrowUp className="size-3.5" />, () =>
          moveBlocks(editor, -1),
        )}
        {item("아래로 이동", "⌘⇧↓", <ArrowDown className="size-3.5" />, () =>
          moveBlocks(editor, 1),
        )}
        <div className="bg-border my-1 h-px" />
        {item(
          "삭제",
          "⌫",
          <Trash2 className="size-3.5" />,
          () => editor.commands.deleteSelection(),
          true,
        )}
      </div>
    </>
  );
}
