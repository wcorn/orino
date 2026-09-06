import type { PrepItemView, PrepSection, PrepSectionOrder } from "../api/prep";

/**
 * 드래그·버튼이 만드는 새 배치(#1364).
 *
 * <p>순서 계산을 화면에서 떼어 둔다. 여기서 틀리면 「끌어다 놓은 자리와 다른 곳에 붙는다」가
 * 되는데, 그건 브라우저를 띄우지 않고는 눈에 안 보이는 종류의 버그다 — 규칙만 따로 세워
 * 단위 테스트로 못 박는다.
 */

/** 평면 목록 한 줄. 묶음 차례대로 편 것이다. */
interface FlatEntry {
  id: number;
  label: string | null;
}

function flatten(sections: PrepSection[]): FlatEntry[] {
  return sections.flatMap((section) =>
    section.items.map((item) => ({ id: item.id, label: section.label })),
  );
}

/** 이어지는 같은 이름끼리 묶는다. 서버는 이 모양 그대로 저장한다. */
function toOrders(entries: FlatEntry[]): PrepSectionOrder[] {
  const orders: PrepSectionOrder[] = [];
  entries.forEach((entry) => {
    const last = orders[orders.length - 1];
    if (last && last.label === entry.label) {
      last.itemIds.push(entry.id);
    } else {
      orders.push({ label: entry.label, itemIds: [entry.id] });
    }
  });
  return orders;
}

/**
 * `activeId`를 `overId`가 있던 자리로 옮긴 배치. 옮길 것이 없으면 `null`이다.
 *
 * <p><b>지나친 줄의 묶음을 따라간다.</b> 아래로 끌면 그 줄 다음, 위로 끌면 그 줄 앞에 서고,
 * 어느 쪽이든 그 줄이 속한 묶음으로 들어간다 — 끄는 동안 보이는 미리보기가 그대로 결과가
 * 되도록 dnd-kit의 자리 계산과 같은 규칙을 쓴다. 여기서 한 칸 어긋나면 「놓은 자리와 다른
 * 데로 갔다」가 된다.
 *
 * <p>자리는 <b>id로 정한다</b>(인덱스가 아니라). 완료 숨기기가 켜져 있으면 화면의 줄 수와
 * 실제 목록의 줄 수가 다른데, 인덱스로 옮기면 안 보이는 줄을 세는 만큼 어긋난다.
 */
export function moveTo(
  sections: PrepSection[],
  activeId: number,
  overId: number,
): PrepSectionOrder[] | null {
  if (activeId === overId) return null;

  const flat = flatten(sections);
  const from = flat.findIndex((entry) => entry.id === activeId);
  const to = flat.findIndex((entry) => entry.id === overId);
  if (from < 0 || to < 0) return null;

  const next = [...flat];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, { ...moved, label: flat[to].label });
  return toOrders(next);
}

/**
 * 새 배치를 화면이 쓰는 묶음 목록으로 되돌린다 — <b>낙관적 반영</b>에 쓴다.
 *
 * <p>손을 뗀 순간 결과가 보여야 한다. 왕복을 기다리면 줄이 제자리로 튀었다가 다시 움직이고,
 * 그건 「내가 놓은 자리가 아닌 데로 갔다」로 읽힌다.
 *
 * <p>배치에 없는 항목(완료 숨기기로 화면에 없던 줄)은 <b>제 묶음에 그대로 남긴다</b> —
 * 서버도 같은 규칙으로 뒤에 붙인다.
 */
export function applyOrders(
  sections: PrepSection[],
  orders: PrepSectionOrder[],
): PrepSection[] {
  const byId = new Map<number, PrepItemView>();
  sections.forEach((section) =>
    section.items.forEach((item) => byId.set(item.id, item)),
  );
  const placed = new Set(orders.flatMap((order) => order.itemIds));

  const next = new Map<string | null, PrepItemView[]>();
  const push = (label: string | null, item: PrepItemView) => {
    const items = next.get(label);
    if (items) items.push(item);
    else next.set(label, [item]);
  };

  orders.forEach((order) =>
    order.itemIds.forEach((id) => {
      const item = byId.get(id);
      if (item) push(order.label, { ...item, sectionLabel: order.label });
    }),
  );
  sections.forEach((section) =>
    section.items.forEach((item) => {
      if (!placed.has(item.id)) push(section.label, item);
    }),
  );

  // 「묶음 없음」은 언제나 맨 앞이다(#1358). 서버가 다시 내려줄 때와 같은 차례여야
  // 재조회 순간 목록이 한 번 더 움직이지 않는다.
  const labels = [...next.keys()];
  const ordered = labels.includes(null)
    ? [null, ...labels.filter((label) => label !== null)]
    : labels;

  return ordered.map((label) => {
    const items = next.get(label) ?? [];
    return {
      label,
      total: items.length,
      done: items.filter((item) => item.done).length,
      items,
    };
  });
}
