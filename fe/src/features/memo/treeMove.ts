import type { MemoTreeNode } from "./api/memos";

export type DropPosition = "inside" | "before" | "after";

/** 이동 결과: 대상 부모(parentId)와 그 부모의 새 자식 순서(id 배열). */
export interface MovePlan {
  parentId: number | null;
  orderedIds: number[];
}

function findNode(nodes: MemoTreeNode[], id: number): MemoTreeNode | null {
  for (const n of nodes) {
    if (n.id === id) return n;
    const found = findNode(n.children, id);
    if (found) return found;
  }
  return null;
}

/** id의 부모 id를 찾는다. 루트면 null, 트리에 없으면 undefined. */
function findParentId(
  nodes: MemoTreeNode[],
  id: number,
  parent: number | null = null,
): number | null | undefined {
  for (const n of nodes) {
    if (n.id === id) return parent;
    const found = findParentId(n.children, id, n.id);
    if (found !== undefined) return found;
  }
  return undefined;
}

/** parentId의 자식 목록(루트는 parentId=null). */
function childrenOf(
  tree: MemoTreeNode[],
  parentId: number | null,
): MemoTreeNode[] {
  if (parentId == null) return tree;
  return findNode(tree, parentId)?.children ?? [];
}

/** candidateId가 rootId 자신이거나 그 서브트리(자손)에 속하면 true. */
function isSelfOrDescendant(
  tree: MemoTreeNode[],
  rootId: number,
  candidateId: number,
): boolean {
  const root = findNode(tree, rootId);
  if (!root) return false;
  const walk = (node: MemoTreeNode): boolean =>
    node.id === candidateId || node.children.some(walk);
  return walk(root);
}

/**
 * 드래그 이동을 계산한다. 순환(자기/자손을 부모로)·무변화(no-op)면 null.
 * position: inside=대상의 마지막 자식으로, before/after=대상의 형제로.
 */
export function computeMove(
  tree: MemoTreeNode[],
  dragId: number,
  targetId: number,
  position: DropPosition,
): MovePlan | null {
  if (dragId === targetId) return null;
  if (findNode(tree, dragId) == null || findNode(tree, targetId) == null) {
    return null;
  }

  const newParentId =
    position === "inside" ? targetId : (findParentId(tree, targetId) ?? null);

  // 순환 방지: 새 부모가 드래그 노드 자신이거나 그 자손이면 불가
  if (newParentId != null && isSelfOrDescendant(tree, dragId, newParentId)) {
    return null;
  }

  const siblings = childrenOf(tree, newParentId).map((n) => n.id);
  const withoutDrag = siblings.filter((id) => id !== dragId);

  let insertIndex: number;
  if (position === "inside") {
    insertIndex = withoutDrag.length; // 마지막 자식으로 append
  } else {
    const targetIndex = withoutDrag.indexOf(targetId);
    insertIndex = position === "before" ? targetIndex : targetIndex + 1;
  }

  const orderedIds = [
    ...withoutDrag.slice(0, insertIndex),
    dragId,
    ...withoutDrag.slice(insertIndex),
  ];

  // 무변화면 no-op: 같은 부모 + 같은 순서
  const currentParentId = findParentId(tree, dragId) ?? null;
  const currentOrder = siblings;
  if (
    currentParentId === newParentId &&
    orderedIds.length === currentOrder.length &&
    orderedIds.every((id, i) => id === currentOrder[i])
  ) {
    return null;
  }

  return { parentId: newParentId, orderedIds };
}
