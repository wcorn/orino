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
  /**
   * 분류마다 이미 있는 묶음 이름들. 고르는 길이 없으면 「캐리어」를 매번 다시 타이핑하다
   * 오타 하나로 묶음이 둘로 갈린다 — 이름이 곧 묶음이라 되돌릴 방법이 목록에서 안 보인다.
   */
  sectionsByCategory: Record<PrepCategory, string[]>;
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
 *
 * <p><b>묶음은 반대로 분류를 옮겨도 따라간다</b>(#1358). 수량은 짐에서만 뜻이 있다고 우리가
 * 정의한 값이지만, 묶음은 사용자가 적은 말이라 조용히 지우면 무엇을 적었는지가 어디에도
 * 안 남는다.
 */
export function PrepItemSheet({
  item,
  category,
  sectionsByCategory,
  onOpenChange,
  onSave,
  onDelete,
}: PrepItemSheetProps) {
  const [title, setTitle] = useState("");
  const [draftCategory, setDraftCategory] = useState<PrepCategory>("TODO");
  const [section, setSection] = useState("");
  const [due, setDue] = useState("");
  const [quantity, setQuantity] = useState("");

  // 다른 항목을 열면 그 항목의 값으로 갈아끼운다. 남아 있으면 방금 닫은 항목의 제목이
  // 다음 항목에 얹혀 보인다.
  useEffect(() => {
    if (!item || !category) return;
    setTitle(item.title);
    setDraftCategory(category);
    setSection(item.sectionLabel ?? "");
    setDue(item.dueDaysBefore === null ? "" : String(item.dueDaysBefore));
    setQuantity(item.quantity === null ? "" : String(item.quantity));
  }, [item, category]);

  if (!item) return null;

  const isBag = draftCategory === "BAG";
  // 옮겨 갈 분류의 묶음을 보여준다 — 지금 분류의 목록을 보여주면 옮긴 뒤에 없는 이름이 된다.
  const suggestions = (sectionsByCategory[draftCategory] ?? []).filter(
    (name) => name !== section.trim(),
  );

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

    const trimmedSection = section.trim();
    if (trimmedSection === "") {
      if (item.sectionLabel !== null) clear.push("SECTION_LABEL");
    } else if (trimmedSection !== item.sectionLabel) {
      // 안 바뀌었으면 아예 안 보낸다. 보내면 서버가 「옮겼다」고 보고 묶음 맨 뒤로 내린다.
      body.sectionLabel = trimmedSection;
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

        {/*
          묶음은 자유 입력이다. 목록에서 고르기만 되면 첫 묶음을 만들 자리가 없고, 입력만
          되면 두 번째부터 같은 이름을 다시 쳐야 한다 — 둘 다 둔다.
        */}
        <div className="flex flex-col gap-2">
          <Input
            value={section}
            maxLength={30}
            aria-label="묶음"
            placeholder="묶음 없음"
            onChange={(event) => setSection(event.currentTarget.value)}
            className="h-10"
          />
          {suggestions.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {suggestions.map((name) => (
                <button
                  key={name}
                  type="button"
                  aria-label={`묶음 ${name}(으)로`}
                  onClick={() => setSection(name)}
                  className="border-border text-muted-foreground min-h-8 rounded-full border px-3 py-1 text-[13px]"
                >
                  {name}
                </button>
              ))}
            </div>
          )}
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
