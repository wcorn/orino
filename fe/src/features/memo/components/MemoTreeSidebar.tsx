import {
  ChevronDown,
  ChevronRight,
  FileText,
  MoreHorizontal,
  Plus,
  Trash2,
} from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Menu, MenuItem } from "@/components/ui/menu";
import { cn } from "@/lib/utils";

import type { MemoTreeNode } from "../api/memos";

interface Props {
  tree: MemoTreeNode[];
  activeMemoId: number | null;
  onSelect: (memoId: number) => void;
  onAddRoot: () => void;
  onAddChild: (parentId: number) => void;
  onRequestDelete: (node: MemoTreeNode) => void;
  addPending?: boolean;
}

export function MemoTreeSidebar({
  tree,
  activeMemoId,
  onSelect,
  onAddRoot,
  onAddChild,
  onRequestDelete,
  addPending,
}: Props) {
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
              onSelect={onSelect}
              onAddChild={onAddChild}
              onRequestDelete={onRequestDelete}
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
  onSelect: (memoId: number) => void;
  onAddChild: (parentId: number) => void;
  onRequestDelete: (node: MemoTreeNode) => void;
}

function MemoTreeItem({
  node,
  depth,
  activeMemoId,
  onSelect,
  onAddChild,
  onRequestDelete,
}: ItemProps) {
  const [expanded, setExpanded] = useState(true);
  const hasChildren = node.children.length > 0;
  const isActive = node.id === activeMemoId;

  return (
    <li>
      <div
        className={cn(
          "group/memo flex items-center gap-0.5 rounded-md pr-1 text-sm",
          isActive
            ? "bg-primary/10 text-primary"
            : "text-foreground/80 hover:bg-muted",
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
              onSelect={onSelect}
              onAddChild={onAddChild}
              onRequestDelete={onRequestDelete}
            />
          ))}
        </ul>
      )}
    </li>
  );
}
