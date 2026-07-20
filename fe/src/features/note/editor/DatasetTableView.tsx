import { type NodeViewProps, NodeViewWrapper } from "@tiptap/react";

import { DatasetGrid } from "../dataset/DatasetGrid";
import { useDatasetTableContext } from "./datasetTableContext";

/**
 * 노트 본문의 데이터 그리드 블록. 노드엔 datasetId만 있고, 실제 표는 별도 dataset
 * 리소스에서 지연 로드해 편집한다. contentEditable=false로 ProseMirror 편집과 분리.
 */
export function DatasetTableView({ node, editor, getPos }: NodeViewProps) {
  const datasetId = node.attrs.datasetId as number | null;
  const { requestDeleteDataset } = useDatasetTableContext();

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
        <DatasetGrid datasetId={datasetId} onDeleteBlock={requestDelete} />
      )}
    </NodeViewWrapper>
  );
}
