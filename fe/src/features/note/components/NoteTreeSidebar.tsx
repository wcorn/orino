import {
  ChevronDown,
  ChevronRight,
  FileText,
  MoreHorizontal,
  Plus,
  Trash2,
} from "lucide-react";
import { type DragEvent, useState } from "react";

import { Button } from "@/components/ui/button";
import { Menu, MenuItem } from "@/components/ui/menu";
import { cn } from "@/lib/utils";

import type { NoteTreeNode } from "../api/notes";
import { computeMove, type DropPosition } from "../treeMove";

interface Props {
  tree: NoteTreeNode[];
  activeNoteId: number | null;
  onSelect: (noteId: number) => void;
  onAddRoot: () => void;
  onRequestDelete: (node: NoteTreeNode) => void;
  /** 하위 노트 추가(트리 메뉴). 제공 시에만 "하위 추가" 메뉴가 노출된다. */
  onAddChild?: (parentId: number) => void;
  /** 드래그 이동/정렬. 제공 시에만 노드가 draggable이 된다. */
  onMove?: (dragId: number, targetId: number, position: DropPosition) => void;
  addPending?: boolean;
}

interface DropInfo {
  id: number;
  pos: DropPosition;
}

export function NoteTreeSidebar({
  tree,
  activeNoteId,
  onSelect,
  onAddRoot,
  onRequestDelete,
  onAddChild,
  onMove,
  addPending,
}: Props) {
  const [dragId, setDragId] = useState<number | null>(null);
  const [dropInfo, setDropInfo] = useState<DropInfo | null>(null);

  const handleDragOver = (e: DragEvent, targetId: number) => {
    if (dragId == null) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const y = e.clientY - rect.top;
    const pos: DropPosition =
      y < rect.height / 3
        ? "before"
        : y > (rect.height * 2) / 3
          ? "after"
          : "inside";
    // 순환·무변화면 드롭 불가 → 인디케이터 숨김
    if (computeMove(tree, dragId, targetId, pos)) {
      e.preventDefault(); // drop 허용
      e.dataTransfer.dropEffect = "move";
      if (dropInfo?.id !== targetId || dropInfo?.pos !== pos) {
        setDropInfo({ id: targetId, pos });
      }
    } else if (dropInfo) {
      setDropInfo(null);
    }
  };

  const handleDrop = (e: DragEvent, targetId: number) => {
    e.preventDefault();
    if (onMove && dragId != null && dropInfo && dropInfo.id === targetId) {
      onMove(dragId, targetId, dropInfo.pos);
    }
    setDragId(null);
    setDropInfo(null);
  };

  const handleDragEnd = () => {
    setDragId(null);
    setDropInfo(null);
  };

  return (
    <nav
      aria-label="노트 목록"
      className="flex w-full flex-col gap-1 md:w-56 md:shrink-0"
    >
      <div className="flex items-center justify-between px-1">
        <span className="text-muted-foreground text-xs font-medium">노트</span>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="새 노트"
          disabled={addPending}
          onClick={onAddRoot}
        >
          <Plus className="size-4" />
        </Button>
      </div>
      {tree.length === 0 ? (
        <p className="text-muted-foreground px-2 py-1 text-xs">
          아직 노트가 없습니다
        </p>
      ) : (
        <ul className="flex flex-col gap-0.5">
          {tree.map((node) => (
            <NoteTreeItem
              key={node.id}
              node={node}
              depth={0}
              activeNoteId={activeNoteId}
              draggable={onMove != null}
              dragId={dragId}
              dropInfo={dropInfo}
              onSelect={onSelect}
              onAddChild={onAddChild}
              onRequestDelete={onRequestDelete}
              onDragStart={setDragId}
              onDragOver={handleDragOver}
              onDrop={handleDrop}
              onDragEnd={handleDragEnd}
            />
          ))}
        </ul>
      )}
    </nav>
  );
}

interface ItemProps {
  node: NoteTreeNode;
  depth: number;
  activeNoteId: number | null;
  draggable: boolean;
  dragId: number | null;
  dropInfo: DropInfo | null;
  onSelect: (noteId: number) => void;
  onAddChild?: (parentId: number) => void;
  onRequestDelete: (node: NoteTreeNode) => void;
  onDragStart: (id: number) => void;
  onDragOver: (e: DragEvent, targetId: number) => void;
  onDrop: (e: DragEvent, targetId: number) => void;
  onDragEnd: () => void;
}

function NoteTreeItem({
  node,
  depth,
  activeNoteId,
  draggable,
  dragId,
  dropInfo,
  onSelect,
  onAddChild,
  onRequestDelete,
  onDragStart,
  onDragOver,
  onDrop,
  onDragEnd,
}: ItemProps) {
  const [expanded, setExpanded] = useState(true);
  const hasChildren = node.children.length > 0;
  const isActive = node.id === activeNoteId;
  const drop = dropInfo?.id === node.id ? dropInfo.pos : null;

  return (
    <li>
      <div
        draggable={draggable}
        onDragStart={(e) => {
          if (!draggable) return;
          onDragStart(node.id);
          e.dataTransfer.effectAllowed = "move";
          // Firefox는 dataTransfer에 데이터가 있어야 드래그를 시작한다.
          e.dataTransfer.setData("text/plain", String(node.id));
        }}
        onDragOver={(e) => onDragOver(e, node.id)}
        onDrop={(e) => onDrop(e, node.id)}
        onDragEnd={onDragEnd}
        className={cn(
          "group/note flex items-center gap-0.5 rounded-md pr-1 text-sm",
          isActive
            ? "bg-primary/10 text-primary"
            : "text-foreground/80 hover:bg-muted",
          dragId === node.id && "opacity-50",
          // 드롭 인디케이터
          drop === "inside" && "ring-primary bg-primary/15 ring-1",
          drop === "before" && "border-primary rounded-none border-t-2",
          drop === "after" && "border-primary rounded-none border-b-2",
        )}
        style={{ paddingLeft: `${depth * 0.75}rem` }}
      >
        {hasChildren ? (
          <button
            type="button"
            aria-label={expanded ? "접기" : "펼치기"}
            onClick={() => setExpanded((v) => !v)}
            className="hover:bg-muted-foreground/10 flex size-5 shrink-0 items-center justify-center rounded"
          >
            {expanded ? (
              <ChevronDown className="size-3.5" />
            ) : (
              <ChevronRight className="size-3.5" />
            )}
          </button>
        ) : (
          <span className="size-5 shrink-0" />
        )}
        <button
          type="button"
          onClick={() => onSelect(node.id)}
          className="flex min-w-0 flex-1 items-center gap-1.5 py-1 text-left"
        >
          <FileText className="size-3.5 shrink-0 opacity-60" />
          <span className="truncate">{node.title}</span>
        </button>

        <Menu
          trigger={
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={`${node.title} 메뉴`}
              className="size-6 shrink-0 opacity-0 group-hover/note:opacity-100 data-[popup-open]:opacity-100"
            >
              <MoreHorizontal className="size-3.5" />
            </Button>
          }
        >
          {onAddChild && (
            <MenuItem onClick={() => onAddChild(node.id)}>
              <Plus className="size-3.5" /> 하위 추가
            </MenuItem>
          )}
          <MenuItem variant="destructive" onClick={() => onRequestDelete(node)}>
            <Trash2 className="size-3.5" /> 삭제
          </MenuItem>
        </Menu>
      </div>
      {hasChildren && expanded && (
        <ul className="flex flex-col gap-0.5">
          {node.children.map((child) => (
            <NoteTreeItem
              key={child.id}
              node={child}
              depth={depth + 1}
              activeNoteId={activeNoteId}
              draggable={draggable}
              dragId={dragId}
              dropInfo={dropInfo}
              onSelect={onSelect}
              onAddChild={onAddChild}
              onRequestDelete={onRequestDelete}
              onDragStart={onDragStart}
              onDragOver={onDragOver}
              onDrop={onDrop}
              onDragEnd={onDragEnd}
            />
          ))}
        </ul>
      )}
    </li>
  );
}
