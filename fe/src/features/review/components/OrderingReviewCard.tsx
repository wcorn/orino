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
import { GripVertical } from "lucide-react";
import { useState } from "react";

import type { OrderingItem } from "@/features/flashcard/api/flashcards";
import { cn } from "@/lib/utils";

import { reorder, shuffleForReview } from "../ordering";

interface Props {
  /** 정답 순서의 항목 배열. */
  items: OrderingItem[];
  /** true면 드래그 잠금 + 위치별 정오 색 표시 + 정답 순서 노출. */
  revealed: boolean;
}

/**
 * 순서 카드 복습 본문. 마운트 시 1회 셔플(정답 회피) → 드래그로 재정렬 → 공개 시 색 비교.
 * 채점(점수·정답률)은 하지 않는다. 위치별 정오를 색으로만 보여준다.
 */
export function OrderingReviewCard({ items, revealed }: Props) {
  const [order, setOrder] = useState<OrderingItem[]>(() =>
    shuffleForReview(items),
  );

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

  const handleDragEnd = (event: DragEndEvent) => {
    if (revealed) return;
    const { active, over } = event;
    if (!over) return;
    setOrder((prev) => reorder(prev, String(active.id), String(over.id)));
  };

  if (revealed) {
    return (
      <div className="flex flex-col gap-4">
        <section className="flex flex-col gap-2">
          <h3 className="text-muted-foreground text-xs font-medium">내 배열</h3>
          <ul className="flex flex-col gap-1.5">
            {order.map((item, i) => {
              const correct = item.id === items[i].id;
              return (
                <li
                  key={item.id}
                  className="border-border flex items-center gap-2 rounded-lg border px-3 py-2"
                >
                  <span
                    aria-label={correct ? "정답 위치" : "오답 위치"}
                    className={cn(
                      "size-3 shrink-0 rounded-[4px]",
                      correct ? "bg-success" : "bg-destructive",
                    )}
                  />
                  <span className="text-sm whitespace-pre-wrap">
                    {item.text}
                  </span>
                </li>
              );
            })}
          </ul>
        </section>

        <section className="flex flex-col gap-2">
          <h3 className="text-muted-foreground text-xs font-medium">
            정답 순서
          </h3>
          <ol className="flex flex-col gap-1.5">
            {items.map((item, i) => (
              <li
                key={item.id}
                className="bg-muted/40 flex items-center gap-2 rounded-lg px-3 py-2"
              >
                <span className="text-muted-foreground w-4 shrink-0 text-center text-xs font-medium tabular-nums">
                  {i + 1}
                </span>
                <span className="text-sm whitespace-pre-wrap">{item.text}</span>
              </li>
            ))}
          </ol>
        </section>
      </div>
    );
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragEnd={handleDragEnd}
    >
      <SortableContext
        items={order.map((i) => i.id)}
        strategy={verticalListSortingStrategy}
      >
        <ul data-ordering-drag className="flex flex-col gap-1.5">
          {order.map((item, index) => (
            <SortableRow key={item.id} item={item} index={index} />
          ))}
        </ul>
      </SortableContext>
    </DndContext>
  );
}

interface RowProps {
  item: OrderingItem;
  index: number;
}

function SortableRow({ item, index }: RowProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: item.id });

  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn(
        "bg-card border-border flex items-center gap-2 rounded-lg border px-3 py-2",
        isDragging && "relative z-10 opacity-80 shadow-sm",
      )}
    >
      <button
        type="button"
        ref={setActivatorNodeRef}
        className="text-muted-foreground hover:text-foreground shrink-0 cursor-grab touch-none active:cursor-grabbing"
        aria-label={`${index + 1}번째 항목 순서 이동`}
        {...attributes}
        {...listeners}
      >
        <GripVertical className="size-4" />
      </button>
      <span className="text-sm whitespace-pre-wrap">{item.text}</span>
    </li>
  );
}
