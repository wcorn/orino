import { ChevronDown, ChevronRight, FileText, Plus } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

import type { NoteTreeNode } from "../api/notes";

interface Props {
  tree: NoteTreeNode[];
  activeNoteId: number | null;
  onSelect: (noteId: number) => void;
  onAddRoot: () => void;
  addRootPending?: boolean;
}

export function NoteTreeSidebar({
  tree,
  activeNoteId,
  onSelect,
  onAddRoot,
  addRootPending,
}: Props) {
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
          disabled={addRootPending}
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
              onSelect={onSelect}
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
  onSelect: (noteId: number) => void;
}

function NoteTreeItem({ node, depth, activeNoteId, onSelect }: ItemProps) {
  const [expanded, setExpanded] = useState(true);
  const hasChildren = node.children.length > 0;
  const isActive = node.id === activeNoteId;

  return (
    <li>
      <div
        className={cn(
          "flex items-center gap-0.5 rounded-md pr-1 text-sm",
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
      </div>
      {hasChildren && expanded && (
        <ul className="flex flex-col gap-0.5">
          {node.children.map((child) => (
            <NoteTreeItem
              key={child.id}
              node={child}
              depth={depth + 1}
              activeNoteId={activeNoteId}
              onSelect={onSelect}
            />
          ))}
        </ul>
      )}
    </li>
  );
}
