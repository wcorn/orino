import { Plus } from "lucide-react";
import { type FormEvent, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Menu, MenuItem } from "@/components/ui/menu";

import type { PrepCategory } from "../api/prep";
import { PREP_CATEGORY_LABEL } from "./categories";

const CATEGORIES: PrepCategory[] = ["DOCUMENT", "BOOKING", "BAG", "TODO"];

interface PrepAddBarProps {
  /** 방금 적은 분류. 다음 항목이 이걸 이어받는다(§13). */
  category: PrepCategory;
  onCategoryChange: (category: PrepCategory) => void;
  offline: boolean;
  onAdd: (title: string) => void;
}

/**
 * 붙박이 입력줄(§10.1 · §13).
 *
 * <p><b>시트를 열고 닫지 않는다.</b> 짐 목록은 스무 개를 연달아 치는 작업이고, 엔터를 치면
 * 입력만 비워지고 포커스는 남아야 한다. 「추가 → 시트 열림 → 저장 → 닫힘 → 다시 열기」는
 * 한 항목마다 네 번을 누르게 만든다.
 *
 * <p>분류는 방금 적은 것을 이어받는다. 바꾸는 길을 옆 메뉴로 열어 두는데, 그것이 없으면
 * 첫 짐 하나를 넣으려고 「할 일로 추가 → 시트에서 분류 변경」을 거쳐야 한다 —
 * 「추가된 분류는 자동으로 펼친다」(§10.6)는 규칙도 그때는 쓸 일이 없어진다.
 */
export function PrepAddBar({
  category,
  onCategoryChange,
  offline,
  onAdd,
}: PrepAddBarProps) {
  const [title, setTitle] = useState("");

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = title.trim();
    if (!trimmed || offline) return;
    onAdd(trimmed);
    // 입력만 비운다. 포커스는 그대로 둬서 다음 줄을 바로 칠 수 있다.
    setTitle("");
  };

  return (
    <form
      onSubmit={submit}
      className="bg-background sticky bottom-0 flex items-center gap-2.5 border-t pt-3"
    >
      <Menu
        align="start"
        trigger={
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={offline}
            aria-label={`추가할 분류: ${PREP_CATEGORY_LABEL[category]}`}
          >
            <Plus className="size-4" />
            {PREP_CATEGORY_LABEL[category]}
          </Button>
        }
      >
        {CATEGORIES.map((value) => (
          <MenuItem key={value} onClick={() => onCategoryChange(value)}>
            {PREP_CATEGORY_LABEL[value]}
          </MenuItem>
        ))}
      </Menu>

      <Input
        value={title}
        disabled={offline}
        aria-label="준비 항목 추가"
        placeholder={`추가하고 엔터…  (${PREP_CATEGORY_LABEL[category]})`}
        onChange={(event) => setTitle(event.currentTarget.value)}
        className="h-11 flex-1 sm:h-9"
      />

      <p className="text-muted-foreground hidden text-[13px] sm:block">
        분류는 방금 적은 것을 이어받아요
      </p>
    </form>
  );
}
