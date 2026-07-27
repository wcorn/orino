import { type Editor, useEditorState } from "@tiptap/react";
import { BubbleMenu } from "@tiptap/react/menus";
import { Check, ExternalLink, Link2Off, Pencil, X } from "lucide-react";
import { type RefObject, useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import { normalizeUrl } from "../editor/link";

interface Props {
  editor: Editor;
  /** 툴바 버튼·Cmd+K가 링크 편집을 열 수 있도록 open 함수를 등록할 ref. */
  openRef: RefObject<() => void>;
}

/**
 * 링크 버블메뉴. 커서가 링크 위에 있으면 열기·편집·해제 버튼을, 편집 중이면 URL 입력을 보여준다.
 * 툴바 링크 버튼·Cmd+K는 openRef.current()로 편집을 연다(선택 텍스트 또는 기존 링크 대상).
 */
export function LinkMenu({ editor, openRef }: Props) {
  const [editing, setEditing] = useState(false);
  const [url, setUrl] = useState("");
  // shouldShow는 생성 시점 클로저라 최신 editing을 못 본다 → ref로 읽는다.
  const editingRef = useRef(false);
  const inputRef = useRef<HTMLInputElement>(null);

  // 링크 href를 구독해 커서가 링크 안팎을 오갈 때 버블 내용(URL·버튼)이 갱신되게 한다.
  const href = useEditorState({
    editor,
    selector: ({ editor }) =>
      (editor.getAttributes("link").href as string | undefined) ?? "",
  });

  const setEditingState = (v: boolean) => {
    editingRef.current = v;
    setEditing(v);
    // 선택 변화 없이 editing만 바뀌어도 버블이 shouldShow를 다시 평가하도록 트랜잭션을 흘린다.
    editor.view.dispatch(editor.state.tr.setMeta("linkMenu", v));
  };

  // 툴바/단축키에서 호출: 링크 위이거나 텍스트가 선택돼 있을 때만 편집을 연다.
  useEffect(() => {
    openRef.current = () => {
      if (!editor.isActive("link") && editor.state.selection.empty) return;
      setUrl((editor.getAttributes("link").href as string | undefined) ?? "");
      setEditingState(true);
    };
  });

  useEffect(() => {
    if (editing) inputRef.current?.focus();
  }, [editing]);

  const apply = () => {
    const normalized = normalizeUrl(url);
    if (!normalized) {
      remove();
      return;
    }
    editor
      .chain()
      .focus()
      .extendMarkRange("link")
      .setLink({ href: normalized })
      .run();
    setEditingState(false);
  };

  const remove = () => {
    editor.chain().focus().extendMarkRange("link").unsetLink().run();
    setEditingState(false);
  };

  const openHref = () => {
    if (href) window.open(href, "_blank", "noopener,noreferrer");
  };

  return (
    <BubbleMenu
      editor={editor}
      pluginKey="linkMenu"
      shouldShow={({ editor }) =>
        editor.isEditable && (editor.isActive("link") || editingRef.current)
      }
      options={{ placement: "bottom" }}
      className="border-border bg-popover flex items-center gap-0.5 rounded-md border p-1 shadow-md"
    >
      {editing ? (
        <>
          <Input
            ref={inputRef}
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                apply();
              } else if (e.key === "Escape") {
                e.preventDefault();
                setEditingState(false);
              }
            }}
            placeholder="https://example.com"
            aria-label="링크 URL"
            className="h-7 w-56 text-sm"
          />
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="링크 적용"
            onClick={apply}
          >
            <Check className="size-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="편집 취소"
            onClick={() => setEditingState(false)}
          >
            <X className="size-4" />
          </Button>
        </>
      ) : (
        <>
          <a
            href={href || undefined}
            onClick={(e) => {
              e.preventDefault();
              openHref();
            }}
            className="text-muted-foreground max-w-[16rem] truncate px-1 text-sm hover:underline"
            title={href}
          >
            {href}
          </a>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="링크 열기"
            onClick={openHref}
          >
            <ExternalLink className="size-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="링크 편집"
            onClick={() => {
              setUrl(href);
              setEditingState(true);
            }}
          >
            <Pencil className="size-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="링크 해제"
            onClick={remove}
          >
            <Link2Off className="size-4" />
          </Button>
        </>
      )}
    </BubbleMenu>
  );
}
