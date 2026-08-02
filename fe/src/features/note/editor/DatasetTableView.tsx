import { useQueries } from "@tanstack/react-query";
import { NodeSelection } from "@tiptap/pm/state";
import type { Editor } from "@tiptap/react";
import { type NodeViewProps, NodeViewWrapper } from "@tiptap/react";
import { useEffect, useRef } from "react";

import { type DatasetMeta, fetchDatasetMeta } from "../dataset/api/datasets";
import { DatasetGrid } from "../dataset/DatasetGrid";
import { datasetKeys } from "../dataset/queryKeys";
import { useDatasetTableContext } from "./datasetTableContext";

/**
 * 같은 노트의 다른 표들 메타(자기 제외). 표간 참조 피커·저장(tableRefs)에 쓴다. 노트 doc에서
 * datasetTable 노드의 id를 모아 각 표 메타를 조회한다(캐시는 각 그리드가 채운 것과 공유).
 */
function useSiblingTables(editor: Editor, selfId: number): DatasetMeta[] {
  const ids: number[] = [];
  editor.state.doc.descendants((n) => {
    if (n.type.name === "datasetTable") {
      const id = n.attrs.datasetId as number | null;
      if (typeof id === "number" && id !== selfId) ids.push(id);
    }
  });
  const results = useQueries({
    queries: ids.map((id) => ({
      queryKey: datasetKeys.meta(id),
      queryFn: () => fetchDatasetMeta(id),
      staleTime: 60 * 1000,
    })),
  });
  return results.map((r) => r.data).filter((d): d is DatasetMeta => !!d);
}

/**
 * 노트 본문의 데이터 그리드 블록. 노드엔 datasetId만 있고, 실제 표는 별도 dataset
 * 리소스에서 지연 로드해 편집한다. contentEditable=false로 ProseMirror 편집과 분리.
 */
export function DatasetTableView({
  node,
  editor,
  getPos,
  selected,
}: NodeViewProps) {
  const datasetId = node.attrs.datasetId as number | null;
  const { requestDeleteDataset } = useDatasetTableContext();
  const siblingTables = useSiblingTables(editor, datasetId ?? -1);

  // TipTap의 `selected`는 "이 표가 단독 선택됨"이 아니라 "선택이 이 표를 덮음"이다
  // (isNodeViewSelected: from <= pos && to >= pos + nodeSize). 그래서 Cmd+A(문서 전체)나
  // 표를 가로지르는 드래그에서도 참이 된다. 그대로 그리드에 넘기면 표를 건드리지도 않았는데
  // 그리드가 첫 셀을 잡고(DatasetGrid의 blockSelected effect) 셀 입력창이 DOM 포커스를
  // 가져가, 그 뒤 키 입력이 에디터 대신 셀로 새어 Backspace 같은 게 먹통이 됐다.
  // 표'만' 선택된 NodeSelection일 때로 좁힌다 — 원래 의도(표만 골랐을 때 바로 타이핑=편집).
  const selection = editor.state.selection;
  const pos = getPos();
  const soleSelected =
    selected &&
    selection instanceof NodeSelection &&
    typeof pos === "number" &&
    selection.from === pos;

  // 셀을 드래그해 범위 선택하려 할 때 표 블록이 통째로 끌려나오던 문제를 막는다.
  // 표가 NodeSelection으로 선택되면(셀 클릭 시 blockSelected) PM의 MouseDown이 spec.draggable:false를
  // 무시하고 노드의 바깥 DOM에 draggable=true를 심는다(NodeSelection 분기). 그 바깥 DOM은 TipTap이
  // 만든 NodeView 컨테이너(.react-renderer = NodeViewWrapper의 부모)라, 드래그 소스가 그 부모가 된다.
  // 소스가 NodeViewWrapper '위'라 NodeViewWrapper의 onDragStart* prop으로는 못 막는다(부모의 이벤트는
  // 자식으로 전파되지 않음). 그래서 부모 nodeDOM에 직접 네이티브 리스너를 걸어 capture에서 취소한다.
  // 블록 이동(⣿ 핸들)은 이 노드 바깥 요소가 소스라 영향 없다.
  const wrapRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const nodeDom = wrapRef.current?.parentElement;
    if (!nodeDom) return;
    const cancelDrag = (e: DragEvent) => e.preventDefault();
    nodeDom.addEventListener("dragstart", cancelDrag, true);
    return () => nodeDom.removeEventListener("dragstart", cancelDrag, true);
  }, []);

  // 표 삭제(우클릭 메뉴·불러오기 실패 시 블록 제거) → 확인 먼저, 확인 시 dataset 삭제 +
  // 블록 제거를 함께. 블록 제거는 되돌리기 불가(addToHistory:false)라, Cmd+Z로 블록만
  // 되살아나 dataset 없는 고아 표가 생기지 않는다.
  const requestDelete = () => {
    if (datasetId == null) return;
    const removeBlock = () => {
      const pos = getPos();
      if (typeof pos !== "number") return;
      editor.view.dispatch(
        editor.state.tr
          .delete(pos, pos + node.nodeSize)
          .setMeta("addToHistory", false),
      );
    };
    requestDeleteDataset(datasetId, removeBlock);
  };

  return (
    <NodeViewWrapper
      ref={wrapRef}
      as="div"
      data-dataset-table={datasetId ?? ""}
      contentEditable={false}
      // 표만 모바일에서 본문 좌우 패딩(pl-3 pr-3)을 음수 마진으로 상쇄해 카드 폭을 꽉 쓴다.
      // 표는 폭이 곧 읽을 수 있는 열 수라, 글 블록과 좌측선을 맞추는 것보다 폭이 우선이다.
      // 값이 본문 패딩과 짝이므로 한쪽만 바꾸면 표가 카드를 넘치거나 어긋난다.
      className="-mx-3 md:mx-0"
    >
      {datasetId != null && (
        <DatasetGrid
          datasetId={datasetId}
          onDeleteBlock={requestDelete}
          // 표 블록'만' 선택되면(한 번 클릭·키보드 이동 등) 그리드가 첫 셀을 잡아
          // 곧바로 타이핑=편집이 되게 한다(블록만 선택돼 키가 먹통이던 문제 해소).
          // 여러 블록을 함께 선택한 경우엔 잡지 않는다 — 위 soleSelected 주석 참고.
          blockSelected={soleSelected}
          // 표간 참조 피커·저장(tableRefs)에 쓸 같은 노트의 다른 표들.
          siblingTables={siblingTables}
        />
      )}
    </NodeViewWrapper>
  );
}
