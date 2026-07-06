import { describe, expect, it } from "vitest";

import type { NoteTreeNode } from "./api/notes";
import { computeMove } from "./treeMove";

function node(
  id: number,
  children: NoteTreeNode[] = [],
  parentId: number | null = null,
): NoteTreeNode {
  return { id, title: `n${id}`, parentId, sortOrder: 0, children };
}

// 트리: 1, 2(자식 21,22), 3
const tree: NoteTreeNode[] = [
  node(1),
  node(2, [node(21, [], 2), node(22, [], 2)]),
  node(3),
];

describe("computeMove", () => {
  it("inside: 대상의 마지막 자식으로 들어간다", () => {
    expect(computeMove(tree, 1, 2, "inside")).toEqual({
      parentId: 2,
      orderedIds: [21, 22, 1],
    });
  });

  it("before: 대상 앞 형제로 재배치한다", () => {
    expect(computeMove(tree, 3, 1, "before")).toEqual({
      parentId: null,
      orderedIds: [3, 1, 2],
    });
  });

  it("after: 대상 뒤 형제로 재배치한다", () => {
    expect(computeMove(tree, 1, 3, "after")).toEqual({
      parentId: null,
      orderedIds: [2, 3, 1],
    });
  });

  it("같은 부모 내 재정렬 시 드래그 노드를 빼고 다시 끼운다", () => {
    expect(computeMove(tree, 21, 22, "after")).toEqual({
      parentId: 2,
      orderedIds: [22, 21],
    });
  });

  it("자기 자신에게 드롭하면 null", () => {
    expect(computeMove(tree, 2, 2, "inside")).toBeNull();
  });

  it("자손을 부모로 삼는 순환 이동은 null", () => {
    // 2를 자식 21 안으로 → 순환
    expect(computeMove(tree, 2, 21, "inside")).toBeNull();
  });

  it("변화 없는 드롭(같은 자리)은 null", () => {
    // 1을 2 앞에 놓아도 이미 그 순서 → no-op
    expect(computeMove(tree, 1, 2, "before")).toBeNull();
  });

  it("루트 노드 before/after로 최상위 이동", () => {
    // 21을 루트 3 뒤로 → 루트 레벨로 승격
    expect(computeMove(tree, 21, 3, "after")).toEqual({
      parentId: null,
      orderedIds: [1, 2, 3, 21],
    });
  });
});
