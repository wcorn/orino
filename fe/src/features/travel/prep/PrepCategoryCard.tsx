import { ChevronDown, ChevronRight } from "lucide-react";

import type { PrepGroup, PrepItemView } from "../api/prep";
import { PREP_CATEGORY_ICON, PREP_CATEGORY_LABEL } from "./categories";
import { PrepItemRow } from "./PrepItemRow";

interface PrepCategoryCardProps {
  group: PrepGroup;
  open: boolean;
  onToggleOpen: () => void;
  /** 완료 숨기기가 켜져 있다. 리스트에서만 빼고 <b>헤더의 개수는 그대로다</b>. */
  hideDone: boolean;
  offline: boolean;
  onToggleItem: (item: PrepItemView, done: boolean) => void;
  onOpenItem: (item: PrepItemView) => void;
  onDeleteItem: (item: PrepItemView) => void;
}

/**
 * 분류 카드 하나. 헤더 전체가 접기/펼치기 버튼이다(§10.6).
 *
 * <p><b>완료 숨기기는 리스트에서만 뺀다.</b> 헤더의 `9/12`와 미니 게이지는 그대로다 —
 * 숨기는 것과 진행률은 다른 일이고, 둘을 같이 움직이면 켜는 순간 「12개 중 12개 완료」처럼
 * 보여 다 끝난 줄 안다.
 */
export function PrepCategoryCard({
  group,
  open,
  onToggleOpen,
  hideDone,
  offline,
  onToggleItem,
  onOpenItem,
  onDeleteItem,
}: PrepCategoryCardProps) {
  const Icon = PREP_CATEGORY_ICON[group.category];
  const label = PREP_CATEGORY_LABEL[group.category];
  const visible = hideDone ? group.items.filter((i) => !i.done) : group.items;
  const donePercent = group.total === 0 ? 0 : (group.done / group.total) * 100;

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
        <>
          {visible.length === 0 ? (
            <p className="text-muted-foreground px-4 pb-3.5 text-[13px]">
              {hideDone && group.total > 0
                ? "남은 게 없어요"
                : "아직 적은 게 없어요"}
            </p>
          ) : (
            <ul className="border-foreground/10 border-t pb-1">
              {visible.map((item) => (
                <PrepItemRow
                  key={item.id}
                  item={item}
                  offline={offline}
                  onToggle={onToggleItem}
                  onOpen={onOpenItem}
                  onDelete={onDeleteItem}
                />
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}
