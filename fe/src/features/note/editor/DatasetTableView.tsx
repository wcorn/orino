import { useQueries } from "@tanstack/react-query";
import type { Editor } from "@tiptap/react";
import { type NodeViewProps, NodeViewWrapper } from "@tiptap/react";

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
      as="div"
      data-dataset-table={datasetId ?? ""}
      contentEditable={false}
    >
      {datasetId != null && (
        <DatasetGrid
          datasetId={datasetId}
          onDeleteBlock={requestDelete}
          // 표 블록이 선택되면(한 번 클릭·키보드 이동 등) 그리드가 첫 셀을 잡아
          // 곧바로 타이핑=편집이 되게 한다(블록만 선택돼 키가 먹통이던 문제 해소).
          blockSelected={selected}
          // 표간 참조 피커·저장(tableRefs)에 쓸 같은 노트의 다른 표들.
          siblingTables={siblingTables}
        />
      )}
    </NodeViewWrapper>
  );
}
