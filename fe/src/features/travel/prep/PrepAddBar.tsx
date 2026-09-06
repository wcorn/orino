import { Plus } from "lucide-react";
import { type FormEvent, type KeyboardEvent, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Menu, MenuItem } from "@/components/ui/menu";

import type { PrepCategory } from "../api/prep";
import { PREP_CATEGORY_LABEL, PREP_NO_SECTION_LABEL } from "./categories";

const CATEGORIES: PrepCategory[] = ["DOCUMENT", "BOOKING", "BAG", "TODO"];

/** 묶음 이름 길이. 서버의 `section_label VARCHAR(30)`과 같은 수다. */
const SECTION_MAX = 30;

interface PrepAddBarProps {
  /** 방금 적은 분류. 다음 항목이 이걸 이어받는다(§13). */
  category: PrepCategory;
  onCategoryChange: (category: PrepCategory) => void;
  /** 방금 적은 묶음. 분류와 같은 규칙으로 이어받는다(#1358). */
  section: string | null;
  onSectionChange: (section: string | null) => void;
  /** 지금 분류에 이미 있는 묶음 이름들. 고르는 길이 없으면 매번 다시 타이핑하게 된다. */
  sections: string[];
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
 * <p>분류도 묶음도 <b>방금 적은 것을 이어받는다</b>. 「캐리어」를 한 번 고르면 캐리어에 넣을
 * 열 줄을 연달아 칠 수 있다 — 줄마다 묶음을 다시 고르게 하면, 묶는 것이 적는 것보다 오래
 * 걸려서 결국 아무도 안 묶는다(#1358).
 */
export function PrepAddBar({
  category,
  onCategoryChange,
  section,
  onSectionChange,
  sections,
  offline,
  onAdd,
}: PrepAddBarProps) {
  const [title, setTitle] = useState("");
  /** 새 묶음 이름을 치는 중. `null`이면 안 치는 중이다. */
  const [draftSection, setDraftSection] = useState<string | null>(null);
  const titleRef = useRef<HTMLInputElement>(null);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = title.trim();
    if (!trimmed || offline) return;
    onAdd(trimmed);
    // 입력만 비운다. 포커스는 그대로 둬서 다음 줄을 바로 칠 수 있다.
    setTitle("");
  };

  /** 새 묶음을 확정한다. 빈 이름은 만들지 않은 것으로 친다. */
  const commitSection = () => {
    const trimmed = (draftSection ?? "").trim();
    setDraftSection(null);
    if (trimmed) {
      onSectionChange(trimmed);
      // 이름을 지었으면 다음에 칠 것은 항목이다. 손을 옮겨 주지 않으면 매번 다시 클릭한다.
      titleRef.current?.focus();
    }
  };

  const onSectionKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter") {
      // 이 입력은 폼 안에 있다 — 막지 않으면 이름을 짓는 엔터가 항목까지 추가한다.
      event.preventDefault();
      commitSection();
    } else if (event.key === "Escape") {
      setDraftSection(null);
    }
  };

  const sectionName = section ?? PREP_NO_SECTION_LABEL;
  const placeholder = section
    ? `추가하고 엔터…  (${PREP_CATEGORY_LABEL[category]} · ${section})`
    : `추가하고 엔터…  (${PREP_CATEGORY_LABEL[category]})`;

  return (
    <form
      onSubmit={submit}
      className="bg-background sticky bottom-0 flex flex-wrap items-center gap-2 border-t pt-3"
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

      {/*
        묶음은 분류 옆에 붙인다. 「짐 · 캐리어」가 한 줄에 보여야 지금 어디에 적고 있는지
        입력하기 전에 안다 — 적고 나서 목록에서 찾는 것은 되돌리는 일이 된다.
      */}
      {draftSection === null ? (
        <Menu
          align="start"
          trigger={
            <Button
              type="button"
              variant="ghost"
              size="sm"
              disabled={offline}
              aria-label={`추가할 묶음: ${sectionName}`}
              className="text-muted-foreground"
            >
              {sectionName}
            </Button>
          }
        >
          <MenuItem onClick={() => onSectionChange(null)}>
            {PREP_NO_SECTION_LABEL}
          </MenuItem>
          {sections.map((value) => (
            <MenuItem key={value} onClick={() => onSectionChange(value)}>
              {value}
            </MenuItem>
          ))}
          {/* 새 묶음은 여기서 만든다. 편집 시트까지 가야 하면 첫 묶음을 아무도 안 만든다. */}
          <MenuItem onClick={() => setDraftSection("")}>새 묶음…</MenuItem>
        </Menu>
      ) : (
        <Input
          autoFocus
          value={draftSection}
          maxLength={SECTION_MAX}
          aria-label="새 묶음 이름"
          placeholder="캐리어"
          onChange={(event) => setDraftSection(event.currentTarget.value)}
          onKeyDown={onSectionKeyDown}
          // 이름을 쳐 놓고 항목 칸을 눌렀다고 이름이 사라지면, 두 번째부터는 안 만든다.
          onBlur={commitSection}
          className="h-11 w-32 sm:h-9"
        />
      )}

      <Input
        ref={titleRef}
        value={title}
        disabled={offline}
        aria-label="준비 항목 추가"
        placeholder={placeholder}
        onChange={(event) => setTitle(event.currentTarget.value)}
        className="h-11 min-w-40 flex-1 sm:h-9"
      />

      <p className="text-muted-foreground hidden text-[13px] sm:block">
        분류와 묶음은 방금 적은 것을 이어받아요
      </p>
    </form>
  );
}
