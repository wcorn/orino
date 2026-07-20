import { type NodeViewProps, NodeViewWrapper } from "@tiptap/react";

import { DatasetGrid } from "../dataset/DatasetGrid";

/**
 * 노트 본문의 데이터 그리드 블록. 노드엔 datasetId만 있고, 실제 표는 별도 dataset
 * 리소스에서 지연 로드해 편집한다. contentEditable=false로 ProseMirror 편집과 분리.
 */
export function DatasetTableView({ node, deleteNode }: NodeViewProps) {
  const datasetId = node.attrs.datasetId as number | null;
  return (
    <NodeViewWrapper
      as="div"
      data-dataset-table={datasetId ?? ""}
      contentEditable={false}
    >
      {datasetId != null && (
        // 표 삭제는 키보드 단축키 대신 표 우클릭 메뉴에서만 한다(deleteNode로 블록 제거 →
        // 노트가 dataset 정리 확인 다이얼로그를 띄운다).
        <DatasetGrid datasetId={datasetId} onDeleteBlock={deleteNode} />
      )}
    </NodeViewWrapper>
  );
}
