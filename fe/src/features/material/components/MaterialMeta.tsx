import type { Material } from "../api/materials";
import { MATERIAL_TYPE_LABELS } from "../utils";

/** 자료 메타 한 줄: 유형 · 카드 수 · 오늘 복습 수(>0이면 강조). 목록 카드·상세 헤더가 공유한다. */
export function MaterialMeta({ material }: { material: Material }) {
  return (
    <div className="text-muted-foreground flex flex-wrap gap-x-3 text-xs">
      <span>{MATERIAL_TYPE_LABELS[material.type]}</span>
      <span>카드 {material.flashcardCount}장</span>
      <span>
        오늘 복습{" "}
        <span
          className={
            material.dueReviewCount > 0 ? "text-primary font-medium" : undefined
          }
        >
          {material.dueReviewCount}건
        </span>
      </span>
    </div>
  );
}
