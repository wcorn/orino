import { Link } from "react-router-dom";

import { Card, CardContent } from "@/components/ui/card";

import type { Material } from "../api/materials";
import { MATERIAL_TYPE_ICONS, MATERIAL_TYPE_LABELS } from "../utils";

interface Props {
  material: Material;
}

export function MaterialCard({ material }: Props) {
  return (
    <Link
      to={`/planner/materials/${material.id}?tab=note`}
      className="block focus:outline-none"
      aria-label={`${material.title} 상세 열기`}
    >
      <Card className="hover:bg-muted/40 transition-colors">
        <CardContent className="flex items-center gap-4">
          <span
            aria-hidden
            className="text-3xl leading-none"
            title={MATERIAL_TYPE_LABELS[material.type]}
          >
            {MATERIAL_TYPE_ICONS[material.type]}
          </span>
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <h2 className="truncate text-base font-medium">{material.title}</h2>
            <div className="text-muted-foreground flex flex-wrap gap-x-3 text-xs">
              <span>{MATERIAL_TYPE_LABELS[material.type]}</span>
              <span>카드 {material.flashcardCount}장</span>
              <span>
                오늘 복습{" "}
                <span
                  className={
                    material.dueReviewCount > 0
                      ? "text-primary font-medium"
                      : undefined
                  }
                >
                  {material.dueReviewCount}건
                </span>
              </span>
            </div>
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
