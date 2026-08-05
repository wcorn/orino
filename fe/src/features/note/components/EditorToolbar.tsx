import { type Editor, useEditorState } from "@tiptap/react";
import {
  Bold,
  Code,
  Code2,
  FilePlus,
  Heading1,
  Heading2,
  ImagePlus,
  Italic,
  Link2,
  List,
  ListOrdered,
  Quote,
  Sheet,
  Table,
} from "lucide-react";
import { useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { toast } from "@/shared/lib/toast";

import { uploadAndInsertImage } from "../editor/imageUpload";
import { createEmptyDataset } from "../import/datasetImport";
import { ImportDialog } from "../import/ImportDialog";

interface Props {
  editor: Editor | null;
  onInsertPage?: () => void;
  insertPagePending?: boolean;
  /** 링크 편집 UI 열기(선택 텍스트에 링크 추가 또는 기존 링크 편집). */
  onInsertLink?: () => void;
}

interface ToolbarButton {
  label: string;
  icon: typeof Bold;
  isActive: () => boolean;
  onClick: () => void;
}

export function EditorToolbar({
  editor,
  onInsertPage,
  insertPagePending,
  onInsertLink,
}: Props) {
  // selection 변화(마크 토글, 커서 이동 등)에 toolbar 활성 표시가 갱신되도록 구독한다.
  useEditorState({
    editor,
    selector: ({ editor }) => editor?.state.selection.from ?? 0,
  });
  const imageInputRef = useRef<HTMLInputElement>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [insertingTable, setInsertingTable] = useState(false);

  if (!editor) return null;

  // 표 삽입 = 빈 dataset(3×3) 생성 후 datasetTable 블록 삽입. 대용량 표와 동일 저장 방식.
  const handleInsertTable = async () => {
    if (insertingTable) return;
    setInsertingTable(true);
    try {
      const datasetId = await createEmptyDataset();
      editor.chain().focus().insertDatasetTable({ datasetId }).run();
    } catch {
      toast("표를 만들지 못했어요. 잠시 후 다시 시도해 주세요.", "error");
    } finally {
      setInsertingTable(false);
    }
  };

  const buttons: ToolbarButton[] = [
    {
      label: "제목 1",
      icon: Heading1,
      isActive: () => editor.isActive("heading", { level: 1 }),
      onClick: () => editor.chain().focus().toggleHeading({ level: 1 }).run(),
    },
    {
      label: "제목 2",
      icon: Heading2,
      isActive: () => editor.isActive("heading", { level: 2 }),
      onClick: () => editor.chain().focus().toggleHeading({ level: 2 }).run(),
    },
    {
      label: "굵게",
      icon: Bold,
      isActive: () => editor.isActive("bold"),
      onClick: () => editor.chain().focus().toggleBold().run(),
    },
    {
      label: "기울임",
      icon: Italic,
      isActive: () => editor.isActive("italic"),
      onClick: () => editor.chain().focus().toggleItalic().run(),
    },
    {
      label: "글머리 기호",
      icon: List,
      isActive: () => editor.isActive("bulletList"),
      onClick: () => editor.chain().focus().toggleBulletList().run(),
    },
    {
      label: "번호 매기기",
      icon: ListOrdered,
      isActive: () => editor.isActive("orderedList"),
      onClick: () => editor.chain().focus().toggleOrderedList().run(),
    },
    {
      label: "인용",
      icon: Quote,
      isActive: () => editor.isActive("blockquote"),
      onClick: () => editor.chain().focus().toggleBlockquote().run(),
    },
    {
      label: "인라인 코드",
      icon: Code,
      isActive: () => editor.isActive("code"),
      onClick: () => editor.chain().focus().toggleCode().run(),
    },
    {
      label: "코드 블록",
      icon: Code2,
      isActive: () => editor.isActive("codeBlock"),
      onClick: () => editor.chain().focus().toggleCodeBlock().run(),
    },
  ];

  return (
    <div
      role="toolbar"
      aria-label="에디터 도구"
      // 모바일은 한 줄 고정 + 가로 스크롤. 버튼 14개가 flex-wrap으로 2줄이 되면 좁은 화면
      // 상단을 크게 잡아먹었다(#1023). 데스크탑은 폭이 남으니 기존대로 줄바꿈한다.
      // px-0(모바일): 카드 껍데기가 없어 툴바도 본문과 같은 좌측선에서 시작해야 한다.
      className="border-border bg-background flex gap-0.5 overflow-x-auto border-b px-0 py-1 md:flex-wrap md:overflow-x-visible md:rounded-t-md md:p-1"
    >
      {buttons.map((btn) => {
        const Icon = btn.icon;
        return (
          <Button
            key={btn.label}
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label={btn.label}
            aria-pressed={btn.isActive()}
            // shrink-0: 모바일 가로 스크롤 툴바에서 버튼이 찌그러지지 않게 한다.
            className={cn("shrink-0", btn.isActive() && "bg-muted")}
            onClick={btn.onClick}
          >
            <Icon className="size-4" />
          </Button>
        );
      })}
      {onInsertLink && (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label="링크"
          aria-pressed={editor.isActive("link")}
          className={cn("shrink-0", editor.isActive("link") && "bg-muted")}
          // 선택된 텍스트가 있거나 커서가 링크 위에 있을 때만 활성.
          disabled={editor.state.selection.empty && !editor.isActive("link")}
          onClick={onInsertLink}
        >
          <Link2 className="size-4" />
        </Button>
      )}
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        aria-label="표 삽입"
        className="shrink-0"
        disabled={insertingTable}
        onClick={() => void handleInsertTable()}
      >
        <Table className="size-4" />
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        aria-label="이미지 추가"
        className="shrink-0"
        onClick={() => imageInputRef.current?.click()}
      >
        <ImagePlus className="size-4" />
      </Button>
      <input
        ref={imageInputRef}
        type="file"
        accept="image/*"
        multiple
        className="hidden"
        aria-hidden
        onChange={(e) => {
          const files = Array.from(e.target.files ?? []);
          files.forEach((file) => void uploadAndInsertImage(editor, file));
          e.target.value = "";
        }}
      />
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        aria-label="가져오기"
        className="shrink-0"
        onClick={() => setImportOpen(true)}
      >
        <Sheet className="size-4" />
      </Button>
      <ImportDialog
        open={importOpen}
        onOpenChange={setImportOpen}
        onInsert={(node) => editor.chain().focus().insertContent(node).run()}
      />
      {onInsertPage && (
        <>
          <span
            className="bg-border mx-1 w-px shrink-0 self-stretch"
            aria-hidden
          />
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="shrink-0"
            aria-label="하위 페이지 추가"
            disabled={insertPagePending}
            onClick={onInsertPage}
          >
            <FilePlus className="size-4" /> 페이지
          </Button>
        </>
      )}
    </div>
  );
}
