import {
  SortableContext,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { ChevronDown, ChevronRight } from "lucide-react";
import { useState } from "react";

import type { PrepGroup, PrepItemView, PrepSection } from "../api/prep";
import {
  PREP_CATEGORY_ICON,
  PREP_CATEGORY_LABEL,
  PREP_NO_SECTION_LABEL,
} from "./categories";
import { PrepItemRow } from "./PrepItemRow";

interface PrepCategoryCardProps {
  group: PrepGroup;
  open: boolean;
  onToggleOpen: () => void;
  /** 완료 숨기기가 켜져 있다. 리스트에서만 빼고 <b>헤더의 개수는 그대로다</b>. */
  hideDone: boolean;
  offline: boolean;
  /** 손가락으로 길게 눌러 들어온 정렬 모드(#1364). */
  dragMode: boolean;
  pointerFine: boolean;
  onEnterDragMode: () => void;
  /** `activeId`를 `overId`가 있던 자리로 옮긴다 — 드래그와 버튼이 같은 길을 쓴다. */
  onMove: (activeId: number, overId: number) => void;
  onToggleItem: (item: PrepItemView, done: boolean) => void;
  onOpenItem: (item: PrepItemView) => void;
  onDeleteItem: (item: PrepItemView) => void;
}

/** 접힘 상태의 키. 「묶음 없음」과 빈 이름은 애초에 같은 것이라 겹칠 것이 없다. */
const keyOf = (section: PrepSection) => section.label ?? "";

/**
 * 분류 카드 하나. 헤더 전체가 접기/펼치기 버튼이다(§10.6).
 *
 * <p><b>완료 숨기기는 리스트에서만 뺀다.</b> 헤더의 `9/12`와 미니 게이지는 그대로다 —
 * 숨기는 것과 진행률은 다른 일이고, 둘을 같이 움직이면 켜는 순간 「12개 중 12개 완료」처럼
 * 보여 다 끝난 줄 안다.
 *
 * <p><b>묶음 소제목은 이름 붙은 묶음이 하나라도 있을 때만 그린다</b>(#1358). 아무것도 안
 * 나눈 분류에 「묶음 없음」 한 줄이 늘어나면, 안 쓰는 사람에게는 기능이 아니라 군더더기다 —
 * 그때 이 카드는 묶음을 넣기 전과 똑같이 보인다.
 */
export function PrepCategoryCard({
  group,
  open,
  onToggleOpen,
  hideDone,
  offline,
  dragMode,
  pointerFine,
  onEnterDragMode,
  onMove,
  onToggleItem,
  onOpenItem,
  onDeleteItem,
}: PrepCategoryCardProps) {
  /*
    접힌 묶음만 기억한다. 펼친 것을 기억하면 새로 만든 묶음이 접힌 채로 나타나고, 방금 적은
    줄이 어디로 갔는지 알 수 없다 — 묶음은 항목을 적다가 생기는 것이라 목록이 계속 변한다.
  */
  const [closed, setClosed] = useState<string[]>([]);

  const Icon = PREP_CATEGORY_ICON[group.category];
  const label = PREP_CATEGORY_LABEL[group.category];
  const donePercent = group.total === 0 ? 0 : (group.done / group.total) * 100;

  // 서버는 빈 묶음을 내리지 않지만, 실행취소를 기다리는 줄을 화면이 걷어내면 여기서 빈다.
  const sections = group.sections.filter((section) => section.items.length > 0);
  const labeled = sections.some((section) => section.label !== null);
  const visibleOf = (section: PrepSection) =>
    hideDone ? section.items.filter((item) => !item.done) : section.items;
  const empty = sections.every((section) => visibleOf(section).length === 0);

  /*
    위/아래 버튼이 보는 이웃은 <b>화면에 보이는 줄</b>이다 — 완료 숨기기로 감춘 줄을 세면
    한 번 눌렀는데 두 칸 움직인 것처럼 보인다. 묶음 경계도 이 목록에서는 그냥 다음 줄이라,
    끝에서 한 번 더 누르면 다음 묶음의 처음이 된다(드래그와 같은 규칙이다).
  */
  const visibleFlat = sections.flatMap((section) => visibleOf(section));

  const rowsOf = (section: PrepSection) => (
    // 묶음이 없을 때는 이 목록이 곧 카드의 내용이라 여기까지 키가 필요하다.
    <ul key={keyOf(section)} className="border-foreground/10 border-t pb-1">
      {visibleOf(section).map((item) => {
        const at = visibleFlat.findIndex((row) => row.id === item.id);
        return (
          <PrepItemRow
            key={item.id}
            item={item}
            offline={offline}
            dragMode={dragMode}
            pointerFine={pointerFine}
            canMoveUp={at > 0}
            canMoveDown={at < visibleFlat.length - 1}
            onMoveUp={() => onMove(item.id, visibleFlat[at - 1].id)}
            onMoveDown={() => onMove(item.id, visibleFlat[at + 1].id)}
            onEnterDragMode={onEnterDragMode}
            onToggle={onToggleItem}
            onOpen={onOpenItem}
            onDelete={onDeleteItem}
          />
        );
      })}
    </ul>
  );

  return (
    <section className="bg-card ring-foreground/10 rounded-xl ring-1">
      {/*
        이름을 「짐 9/12」로 못박는다. 안 그러면 접근성 이름이 미니 게이지·화살표까지 훑어
        분류마다 길이가 제각각이 되고, 「짐」으로 찾은 것이 입력줄의 분류 버튼과 겹친다.
      */}
      <button
        type="button"
        onClick={onToggleOpen}
        aria-expanded={open}
        aria-label={`${label} ${group.done}/${group.total}`}
        className="flex w-full items-center gap-2.5 px-4 py-3.5 text-left"
      >
        <Icon className="text-muted-foreground size-[15px] shrink-0" />
        <span className="text-[15px] font-semibold">{label}</span>
        <span className="text-muted-foreground text-sm tabular-nums">
          {group.done}/{group.total}
        </span>

        <span className="bg-muted ml-auto h-1.5 w-[88px] overflow-hidden rounded-full">
          <span
            className="bg-primary block h-full rounded-full"
            style={{ width: `${donePercent}%` }}
          />
        </span>
        {open ? (
          <ChevronDown className="text-muted-foreground size-4 shrink-0" />
        ) : (
          <ChevronRight className="text-muted-foreground size-4 shrink-0" />
        )}
      </button>

      {open && (
        /*
          정렬은 <b>분류 카드 안에서만</b> 일어난다(#1364). 분류를 넘는 이동은 편집 시트가
          한다 — 카드가 접혀 있을 수 있어 끌어다 놓을 자리가 없는 경우가 생긴다.
        */
        <SortableContext
          items={visibleFlat.map((item) => item.id)}
          strategy={verticalListSortingStrategy}
        >
          {empty ? (
            <p className="text-muted-foreground px-4 pb-3.5 text-[13px]">
              {hideDone && group.total > 0
                ? "남은 게 없어요"
                : "아직 적은 게 없어요"}
            </p>
          ) : (
            sections.map((section) => {
              if (!labeled) return rowsOf(section);

              const key = keyOf(section);
              const sectionOpen = !closed.includes(key);
              const name = section.label ?? PREP_NO_SECTION_LABEL;

              return (
                <div key={key}>
                  {/*
                    소제목도 헤더 전체가 버튼이다 — 분류 카드와 같은 손놀림이라야 한 화면에서
                    두 겹을 여닫는 것이 하나의 동작으로 읽힌다.
                  */}
                  <button
                    type="button"
                    aria-expanded={sectionOpen}
                    aria-label={`${name} ${section.done}/${section.total}`}
                    onClick={() =>
                      setClosed((prev) =>
                        prev.includes(key)
                          ? prev.filter((c) => c !== key)
                          : [...prev, key],
                      )
                    }
                    className="border-foreground/10 flex w-full items-center gap-2 border-t px-4 py-2 text-left"
                  >
                    <span className="text-muted-foreground text-[13px] font-medium">
                      {name}
                    </span>
                    <span className="text-muted-foreground text-xs tabular-nums">
                      {section.done}/{section.total}
                    </span>
                    {sectionOpen ? (
                      <ChevronDown className="text-muted-foreground ml-auto size-3.5 shrink-0" />
                    ) : (
                      <ChevronRight className="text-muted-foreground ml-auto size-3.5 shrink-0" />
                    )}
                  </button>

                  {sectionOpen &&
                    (visibleOf(section).length === 0 ? (
                      <p className="text-muted-foreground px-4 pb-2 text-[13px]">
                        남은 게 없어요
                      </p>
                    ) : (
                      rowsOf(section)
                    ))}
                </div>
              );
            })
          )}
        </SortableContext>
      )}
    </section>
  );
}
