import { Link } from "react-router-dom";

import { Card, CardContent } from "@/components/ui/card";

import type { Material } from "../api/materials";
import { MATERIAL_TYPE_ICONS, MATERIAL_TYPE_LABELS } from "../utils";
import { MaterialMeta } from "./MaterialMeta";

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
            <MaterialMeta material={material} />
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
