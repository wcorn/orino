import { describe, expect, it } from "vitest";

import type { PrepItemView, PrepSection } from "../api/prep";
import { applyOrders, moveSection, moveTo } from "./reorder";

/**
 * 순서 계산(#1364). <b>규칙만 따로 못 박는다</b> — 화면에서 이걸 틀리면 「끌어다 놓은
 * 자리와 다른 곳에 붙는다」가 되는데, 브라우저를 띄우지 않고는 눈에 안 보이는 버그다.
 */

function item(id: number, title: string, done = false): PrepItemView {
  return {
    id,
    title,
    done,
    sectionLabel: null,
    quantity: null,
    dueDaysBefore: null,
    dueDate: null,
    overdue: false,
    url: null,
    memo: null,
    displayOrder: id,
  };
}

function section(label: string | null, items: PrepItemView[]): PrepSection {
  return {
    label,
    total: items.length,
    done: items.filter((row) => row.done).length,
    items: items.map((row) => ({ ...row, sectionLabel: label })),
  };
}

/** 묶음 없음 2줄 + 캐리어 2줄 + 세면백 1줄. */
const sections: PrepSection[] = [
  section(null, [item(1, "여권 지갑"), item(2, "우산")]),
  section("캐리어", [item(3, "충전기"), item(4, "옷")]),
  section("세면백", [item(5, "칫솔")]),
];

describe("moveTo", () => {
  it("같은 묶음 안에서 자리를 바꾼다", () => {
    expect(moveTo(sections, 4, 3)).toEqual([
      { label: null, itemIds: [1, 2] },
      { label: "캐리어", itemIds: [4, 3] },
      { label: "세면백", itemIds: [5] },
    ]);
  });

  it("다른 묶음의 줄 위에 놓으면 그 묶음으로 들어간다", () => {
    // 「우산」을 캐리어의 「옷」까지 끌어내렸다 — 아래로 끌면 지나친 줄 다음에 놓인다
    // (드래그 중 보이는 미리보기가 그렇게 움직인다).
    expect(moveTo(sections, 2, 4)).toEqual([
      { label: null, itemIds: [1] },
      { label: "캐리어", itemIds: [3, 4, 2] },
      { label: "세면백", itemIds: [5] },
    ]);
  });

  it("위로 끌면 그 줄 앞에 놓인다", () => {
    // 「충전기」를 묶음 없음의 「우산」까지 끌어올렸다.
    expect(moveTo(sections, 3, 2)).toEqual([
      { label: null, itemIds: [1, 3, 2] },
      { label: "캐리어", itemIds: [4] },
      { label: "세면백", itemIds: [5] },
    ]);
  });

  it("묶음 없음 쪽으로 끌면 묶음에서 빠진다", () => {
    // 맨 위 줄까지 끌어올린 것이라 그 앞에 선다.
    expect(moveTo(sections, 5, 1)).toEqual([
      { label: null, itemIds: [5, 1, 2] },
      { label: "캐리어", itemIds: [3, 4] },
    ]);
  });

  it("묶음의 마지막 줄을 다음 묶음의 첫 줄 자리로 옮기면 그 묶음이 된다", () => {
    // 위/아래 버튼이 묶음 경계를 넘는 길이기도 하다 — 끝에서 한 번 더 누르면 다음 묶음이다.
    expect(moveTo(sections, 4, 5)).toEqual([
      { label: null, itemIds: [1, 2] },
      { label: "캐리어", itemIds: [3] },
      { label: "세면백", itemIds: [5, 4] },
    ]);
  });

  it("제자리이거나 없는 줄이면 아무것도 하지 않는다", () => {
    expect(moveTo(sections, 3, 3)).toBeNull();
    expect(moveTo(sections, 99, 1)).toBeNull();
    expect(moveTo(sections, 1, 99)).toBeNull();
  });
});

describe("applyOrders", () => {
  it("배치대로 묶음을 다시 만들고 개수를 다시 센다", () => {
    const done = [
      section(null, [item(1, "여권 지갑", true)]),
      section("캐리어", [item(2, "충전기"), item(3, "옷", true)]),
    ];

    expect(
      applyOrders(done, [
        { label: null, itemIds: [1, 3] },
        { label: "캐리어", itemIds: [2] },
      ]),
    ).toEqual([
      {
        label: null,
        total: 2,
        done: 2,
        items: [
          expect.objectContaining({ id: 1, sectionLabel: null }),
          expect.objectContaining({ id: 3, sectionLabel: null }),
        ],
      },
      {
        label: "캐리어",
        total: 1,
        done: 0,
        items: [expect.objectContaining({ id: 2, sectionLabel: "캐리어" })],
      },
    ]);
  });

  it("배치에 없는 줄은 제 묶음에 그대로 남는다 — 완료 숨기기로 화면에 없던 줄이다", () => {
    const result = applyOrders(sections, [
      { label: "캐리어", itemIds: [4, 3] },
    ]);

    // 서버도 같은 규칙으로 보내지 않은 항목을 뒤에 붙인다.
    expect(
      result.map((row) => [row.label, row.items.map((i) => i.id)]),
    ).toEqual([
      [null, [1, 2]],
      ["캐리어", [4, 3]],
      ["세면백", [5]],
    ]);
  });

  it("묶음 없음은 언제나 맨 앞이다 — 서버가 다시 내려줄 때와 같은 차례여야 한다", () => {
    const result = applyOrders(sections, [
      { label: "캐리어", itemIds: [3, 4] },
      { label: null, itemIds: [1, 2] },
      { label: "세면백", itemIds: [5] },
    ]);

    expect(result.map((row) => row.label)).toEqual([null, "캐리어", "세면백"]);
  });
});

describe("moveSection", () => {
  it("묶음을 다른 묶음 자리로 옮긴다 — 안의 항목은 그대로다", () => {
    expect(moveSection(sections, "세면백", "캐리어")).toEqual([
      { label: null, itemIds: [1, 2] },
      { label: "세면백", itemIds: [5] },
      { label: "캐리어", itemIds: [3, 4] },
    ]);
  });

  it("묶음 없음은 움직이지도, 그 자리를 내주지도 않는다", () => {
    // 이름을 안 붙인 것이 분류의 기본 상태라 언제나 맨 위다(#1358).
    expect(moveSection(sections, "캐리어", null)).toBeNull();
  });

  it("제자리이거나 없는 이름이면 아무것도 하지 않는다", () => {
    expect(moveSection(sections, "캐리어", "캐리어")).toBeNull();
    expect(moveSection(sections, "없는묶음", "캐리어")).toBeNull();
    expect(moveSection(sections, "캐리어", "없는묶음")).toBeNull();
  });

  it("빈 묶음은 배치에서 빠진다 — 서버가 빈 목록을 받지 않는다", () => {
    // 실행취소를 기다리는 줄을 화면이 걷어낸 사이에 이런 묶음이 잠깐 생긴다.
    const withEmpty = [
      section(null, [item(1, "여권 지갑")]),
      section("캐리어", []),
      section("세면백", [item(5, "칫솔")]),
    ];

    expect(moveSection(withEmpty, "세면백", "캐리어")).toEqual([
      { label: null, itemIds: [1] },
      { label: "세면백", itemIds: [5] },
    ]);
  });
});
