import { type Editor, useEditorState } from "@tiptap/react";
import {
  Bold,
  Code,
  Code2,
  Columns3,
  FilePlus,
  Heading1,
  Heading2,
  ImagePlus,
  Italic,
  List,
  ListOrdered,
  Quote,
  Rows3,
  Sheet,
  Table,
  Trash2,
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
}: Props) {
  // selection 변화(표 진입/이탈, 마크 토글 등)에 toolbar가 리렌더되도록 구독한다.
  const isInTable = useEditorState({
    editor,
    selector: ({ editor }) => editor?.isActive("table") ?? false,
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
      className="border-border bg-background flex flex-wrap gap-0.5 rounded-t-md border-b p-1"
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
            className={cn(btn.isActive() && "bg-muted")}
            onClick={btn.onClick}
          >
            <Icon className="size-4" />
          </Button>
        );
      })}
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        aria-label="표 삽입"
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
          <span className="bg-border mx-1 w-px self-stretch" aria-hidden />
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-label="하위 페이지 추가"
            disabled={insertPagePending}
            onClick={onInsertPage}
          >
            <FilePlus className="size-4" /> 페이지
          </Button>
        </>
      )}

      {isInTable && (
        <div
          role="group"
          aria-label="표 편집"
          className="flex w-full flex-wrap items-center gap-0.5 pt-1"
        >
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-label="열 추가"
            onClick={() => editor.chain().focus().addColumnAfter().run()}
          >
            <Columns3 className="size-4" /> 열+
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-label="열 삭제"
            onClick={() => editor.chain().focus().deleteColumn().run()}
          >
            <Columns3 className="size-4" /> 열−
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-label="행 추가"
            onClick={() => editor.chain().focus().addRowAfter().run()}
          >
            <Rows3 className="size-4" /> 행+
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-label="행 삭제"
            onClick={() => editor.chain().focus().deleteRow().run()}
          >
            <Rows3 className="size-4" /> 행−
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-label="헤더 행 전환"
            onClick={() => editor.chain().focus().toggleHeaderRow().run()}
          >
            <Table className="size-4" /> 헤더
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-label="표 삭제"
            onClick={() => editor.chain().focus().deleteTable().run()}
          >
            <Trash2 className="size-4" /> 표 삭제
          </Button>
        </div>
      )}
    </div>
  );
}
