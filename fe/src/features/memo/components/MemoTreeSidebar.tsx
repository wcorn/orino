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

import type { MemoTreeNode } from "../api/memos";
import { computeMove, type DropPosition } from "../treeMove";

interface Props {
  tree: MemoTreeNode[];
  activeMemoId: number | null;
  onSelect: (memoId: number) => void;
  onAddRoot: () => void;
  onAddChild: (parentId: number) => void;
  onRequestDelete: (node: MemoTreeNode) => void;
  /** 드래그 이동/정렬: 드래그한 노드를 대상 기준 위치로 옮긴다. */
  onMove: (dragId: number, targetId: number, position: DropPosition) => void;
  addPending?: boolean;
}

interface DropInfo {
  id: number;
  pos: DropPosition;
}

export function MemoTreeSidebar({
  tree,
  activeMemoId,
  onSelect,
  onAddRoot,
  onAddChild,
  onRequestDelete,
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
    if (dragId != null && dropInfo && dropInfo.id === targetId) {
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
      aria-label="메모 목록"
      className="flex w-full flex-col gap-1 md:w-56 md:shrink-0"
    >
      <div className="flex items-center justify-between px-1">
        <span className="text-muted-foreground text-xs font-medium">메모</span>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="새 메모"
          disabled={addPending}
          onClick={onAddRoot}
        >
          <Plus className="size-4" />
        </Button>
      </div>
      {tree.length === 0 ? (
        <p className="text-muted-foreground px-2 py-1 text-xs">
          아직 메모가 없습니다
        </p>
      ) : (
        <ul className="flex flex-col gap-0.5">
          {tree.map((node) => (
            <MemoTreeItem
              key={node.id}
              node={node}
              depth={0}
              activeMemoId={activeMemoId}
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
  node: MemoTreeNode;
  depth: number;
  activeMemoId: number | null;
  dragId: number | null;
  dropInfo: DropInfo | null;
  onSelect: (memoId: number) => void;
  onAddChild: (parentId: number) => void;
  onRequestDelete: (node: MemoTreeNode) => void;
  onDragStart: (id: number) => void;
  onDragOver: (e: DragEvent, targetId: number) => void;
  onDrop: (e: DragEvent, targetId: number) => void;
  onDragEnd: () => void;
}

function MemoTreeItem({
  node,
  depth,
  activeMemoId,
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
  const isActive = node.id === activeMemoId;
  const drop = dropInfo?.id === node.id ? dropInfo.pos : null;

  return (
    <li>
      <div
        draggable
        onDragStart={(e) => {
          onDragStart(node.id);
          e.dataTransfer.effectAllowed = "move";
          // Firefox는 dataTransfer에 데이터가 있어야 드래그를 시작한다.
          e.dataTransfer.setData("text/plain", String(node.id));
        }}
        onDragOver={(e) => onDragOver(e, node.id)}
        onDrop={(e) => onDrop(e, node.id)}
        onDragEnd={onDragEnd}
        className={cn(
          "group/memo flex items-center gap-0.5 rounded-md pr-1 text-sm",
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
              className="size-6 shrink-0 opacity-0 group-hover/memo:opacity-100 data-[popup-open]:opacity-100"
            >
              <MoreHorizontal className="size-3.5" />
            </Button>
          }
        >
          <MenuItem onClick={() => onAddChild(node.id)}>
            <Plus className="size-3.5" /> 하위 추가
          </MenuItem>
          <MenuItem variant="destructive" onClick={() => onRequestDelete(node)}>
            <Trash2 className="size-3.5" /> 삭제
          </MenuItem>
        </Menu>
      </div>
      {hasChildren && expanded && (
        <ul className="flex flex-col gap-0.5">
          {node.children.map((child) => (
            <MemoTreeItem
              key={child.id}
              node={child}
              depth={depth + 1}
              activeMemoId={activeMemoId}
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
