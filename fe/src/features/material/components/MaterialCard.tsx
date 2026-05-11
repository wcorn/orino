import { BookOpen, GraduationCap, NotebookPen, Video } from "lucide-react";
import { Link } from "react-router-dom";

import { Card, CardContent } from "@/components/ui/card";

import type { MaterialSummary, MaterialType } from "../api/materials";
import { MaterialProgress } from "./MaterialProgress";

const TYPE_META: Record<
  MaterialType,
  { icon: typeof BookOpen; label: string }
> = {
  BOOK: { icon: BookOpen, label: "책" },
  LECTURE: { icon: Video, label: "강의" },
  WORKBOOK: { icon: NotebookPen, label: "문제집" },
  MOOC: { icon: GraduationCap, label: "MOOC" },
};

interface MaterialCardProps {
  material: MaterialSummary;
}

export function MaterialCard({ material }: MaterialCardProps) {
  const meta = TYPE_META[material.type];
  const Icon = meta.icon;

  return (
    <Card
      size="sm"
      className="hover:ring-primary/40 cursor-pointer transition-shadow"
    >
      <CardContent>
        <Link
          to={`/planner/materials/${material.id}`}
          className="flex flex-col gap-2.5"
          aria-label={`${material.title} 상세 보기`}
        >
          <div className="flex items-center gap-2">
            <Icon className="text-primary size-4" />
            <span className="font-medium">{material.title}</span>
            <span className="text-muted-foreground text-xs">
              · {meta.label}
            </span>
            {material.status === "COMPLETED" && (
              <span className="bg-muted text-muted-foreground ml-auto rounded-full px-2 py-0.5 text-xs">
                완료
              </span>
            )}
          </div>
          <MaterialProgress
            completed={material.completedUnits}
            total={material.totalUnits}
          />
        </Link>
      </CardContent>
    </Card>
  );
}
