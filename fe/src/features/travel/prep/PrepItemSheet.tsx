import { useEffect, useState } from "react";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

import type {
  PrepCategory,
  PrepField,
  PrepItemView,
  PrepPatchRequest,
} from "../api/prep";
import { PREP_CATEGORY_LABEL } from "./categories";

const CATEGORIES: PrepCategory[] = ["DOCUMENT", "BOOKING", "BAG", "TODO"];

interface PrepItemSheetProps {
  /** 열려 있는 항목. 닫혀 있으면 `null`이다. */
  item: PrepItemView | null;
  category: PrepCategory | null;
  onOpenChange: (open: boolean) => void;
  onSave: (itemId: number, body: PrepPatchRequest) => void;
  onDelete: (item: PrepItemView) => void;
}

/**
 * 항목 편집 시트(§10.1).
 *
 * <p><b>날짜 칸을 두지 않는다.</b> 기한은 `[출발] [14] [일 전]` 숫자 하나다(§12) — 날짜를
 * 받으면 출발일이 바뀔 때 조용히 하루 늦은 기한이 되고, 화면은 멀쩡해 보인다.
 *
 * <p>수량은 짐에서만 활성이다. 다른 분류로 옮기면 서버가 값을 떨어뜨리므로, 여기서도 칸을
 * 잠가 「적었는데 사라졌다」가 생기지 않게 한다.
 */
export function PrepItemSheet({
  item,
  category,
  onOpenChange,
  onSave,
  onDelete,
}: PrepItemSheetProps) {
  const [title, setTitle] = useState("");
  const [draftCategory, setDraftCategory] = useState<PrepCategory>("TODO");
  const [due, setDue] = useState("");
  const [quantity, setQuantity] = useState("");

  // 다른 항목을 열면 그 항목의 값으로 갈아끼운다. 남아 있으면 방금 닫은 항목의 제목이
  // 다음 항목에 얹혀 보인다.
  useEffect(() => {
    if (!item || !category) return;
    setTitle(item.title);
    setDraftCategory(category);
    setDue(item.dueDaysBefore === null ? "" : String(item.dueDaysBefore));
    setQuantity(item.quantity === null ? "" : String(item.quantity));
  }, [item, category]);

  if (!item) return null;

  const isBag = draftCategory === "BAG";

  const save = () => {
    const trimmed = title.trim();
    if (!trimmed) return;

    // 「안 보냄」과 「비워 달라」는 다르다. 비운 칸은 이름으로 적어 보낸다.
    const clear: PrepField[] = [];
    const body: PrepPatchRequest = { title: trimmed, category: draftCategory };

    if (due.trim() === "") {
      if (item.dueDaysBefore !== null) clear.push("DUE_DAYS_BEFORE");
    } else {
      body.dueDaysBefore = Number(due);
    }

    if (!isBag || quantity.trim() === "") {
      if (item.quantity !== null) clear.push("QUANTITY");
    } else {
      body.quantity = Number(quantity);
    }

    if (clear.length > 0) body.clear = clear;
    onSave(item.id, body);
    onOpenChange(false);
  };

  return (
    <BottomSheet open onOpenChange={onOpenChange} title="항목 편집">
      <div className="flex flex-col gap-4">
        <Input
          value={title}
          aria-label="제목"
          onChange={(event) => setTitle(event.currentTarget.value)}
          className="text-heading h-12 font-semibold"
        />

        <div className="flex flex-wrap gap-2">
          {CATEGORIES.map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={draftCategory === value}
              onClick={() => setDraftCategory(value)}
              className={cn(
                "min-h-9 rounded-full border px-3.5 py-2 text-sm",
                draftCategory === value
                  ? "border-primary bg-primary/10 text-primary font-semibold"
                  : "border-border bg-background text-muted-foreground",
              )}
            >
              {PREP_CATEGORY_LABEL[value]}
            </button>
          ))}
        </div>

        {/* 「출발 N일 전」. 날짜를 고르는 자리가 아니다. */}
        <div className="flex items-center gap-2">
          <span className="text-muted-foreground text-sm">출발</span>
          <Input
            value={due}
            inputMode="numeric"
            aria-label="기한 (출발 며칠 전)"
            placeholder="—"
            onChange={(event) =>
              setDue(event.currentTarget.value.replace(/[^0-9]/g, ""))
            }
            className="h-10 w-20 text-center tabular-nums"
          />
          <span className="text-muted-foreground text-sm">일 전</span>
        </div>

        <div className="flex items-center gap-2">
          <span
            className={cn(
              "text-sm",
              isBag ? "text-muted-foreground" : "text-muted-foreground/50",
            )}
          >
            수량
          </span>
          <Input
            value={isBag ? quantity : ""}
            disabled={!isBag}
            inputMode="numeric"
            aria-label="수량"
            placeholder={isBag ? "—" : "짐에서만 적어요"}
            onChange={(event) =>
              setQuantity(event.currentTarget.value.replace(/[^0-9]/g, ""))
            }
            className="h-10 w-20 text-center tabular-nums"
          />
        </div>

        <div className="flex items-center justify-between pt-1">
          <Button
            type="button"
            variant="ghost"
            className="text-destructive"
            onClick={() => {
              onDelete(item);
              onOpenChange(false);
            }}
          >
            삭제
          </Button>
          <Button type="button" onClick={save} disabled={!title.trim()}>
            저장
          </Button>
        </div>
      </div>
    </BottomSheet>
  );
}
