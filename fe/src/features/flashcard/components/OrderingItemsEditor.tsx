import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical, Plus, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

import type { OrderingItem } from "../api/flashcards";
import {
  createOrderingItem,
  MAX_ORDERING_ITEMS,
  MIN_ORDERING_ITEMS,
  reorderItems,
} from "../orderingItems";

interface Props {
  items: OrderingItem[];
  onChange: (next: OrderingItem[]) => void;
  maxLen: number;
}

/**
 * 순서 카드 항목 에디터. 화면 순서가 곧 정답 순서다.
 * - @dnd-kit sortable(핸들만 잡기, PointerSensor 8px 임계값, arrayMove)
 * - 3~7개: 3개 미만이면 저장 비활성(상위에서 판정), 7개 도달 시 추가 비활성
 */
export function OrderingItemsEditor({ items, onChange, maxLen }: Props) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over) return;
    onChange(reorderItems(items, String(active.id), String(over.id)));
  };

  const updateText = (id: string, text: string) => {
    onChange(items.map((i) => (i.id === id ? { ...i, text } : i)));
  };

  const removeItem = (id: string) => {
    onChange(items.filter((i) => i.id !== id));
  };

  const addItem = () => {
    if (items.length >= MAX_ORDERING_ITEMS) return;
    onChange([...items, createOrderingItem()]);
  };

  const belowMin = items.length < MIN_ORDERING_ITEMS;

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium">항목 (정답 순서로 입력)</span>
        <span
          className={cn(
            "text-muted-foreground text-xs",
            belowMin && "text-destructive",
          )}
        >
          {items.length} / {MAX_ORDERING_ITEMS}
        </span>
      </div>

      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragEnd={handleDragEnd}
      >
        <SortableContext
          items={items.map((i) => i.id)}
          strategy={verticalListSortingStrategy}
        >
          <ul className="flex flex-col gap-2">
            {items.map((item, index) => (
              <SortableRow
                key={item.id}
                item={item}
                index={index}
                maxLen={maxLen}
                canDelete={items.length > MIN_ORDERING_ITEMS}
                onTextChange={(text) => updateText(item.id, text)}
                onRemove={() => removeItem(item.id)}
              />
            ))}
          </ul>
        </SortableContext>
      </DndContext>

      <Button
        type="button"
        variant="outline"
        size="sm"
        className="self-start"
        onClick={addItem}
        disabled={items.length >= MAX_ORDERING_ITEMS}
      >
        <Plus className="size-3.5" /> 항목 추가
      </Button>

      {belowMin && (
        <span className="text-destructive text-xs">
          최소 {MIN_ORDERING_ITEMS}개 항목이 필요해요.
        </span>
      )}
    </div>
  );
}

interface RowProps {
  item: OrderingItem;
  index: number;
  maxLen: number;
  canDelete: boolean;
  onTextChange: (text: string) => void;
  onRemove: () => void;
}

function SortableRow({
  item,
  index,
  maxLen,
  canDelete,
  onTextChange,
  onRemove,
}: RowProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: item.id });

  const over = item.text.length > maxLen;

  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn(
        "bg-background flex items-center gap-2",
        isDragging && "relative z-10 opacity-80",
      )}
    >
      <button
        type="button"
        ref={setActivatorNodeRef}
        className="text-muted-foreground hover:text-foreground shrink-0 cursor-grab touch-none active:cursor-grabbing"
        aria-label={`항목 ${index + 1} 순서 이동`}
        {...attributes}
        {...listeners}
      >
        <GripVertical className="size-4" />
      </button>
      <span className="text-muted-foreground w-4 shrink-0 text-center text-xs font-medium tabular-nums">
        {index + 1}
      </span>
      <Input
        value={item.text}
        onChange={(e) => onTextChange(e.target.value)}
        aria-label={`항목 ${index + 1}`}
        aria-invalid={over || undefined}
        placeholder="항목 내용"
      />
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        className="text-muted-foreground hover:text-destructive shrink-0"
        aria-label={`항목 ${index + 1} 삭제`}
        onClick={onRemove}
        disabled={!canDelete}
      >
        <Trash2 className="size-4" />
      </Button>
    </li>
  );
}
