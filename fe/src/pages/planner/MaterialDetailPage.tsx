import { ChevronLeft, Plus, Settings2 } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";
import type { UnitSummary } from "@/features/material/api/materials";
import { AddUnitsDialog } from "@/features/material/components/AddUnitsDialog";
import { EditMaterialDialog } from "@/features/material/components/EditMaterialDialog";
import { EditUnitDialog } from "@/features/material/components/EditUnitDialog";
import { MaterialProgress } from "@/features/material/components/MaterialProgress";
import { UnitItem } from "@/features/material/components/UnitItem";
import {
  useDeleteMaterial,
  useMaterial,
} from "@/features/material/hooks/useMaterials";
import {
  useCompleteUnit,
  useDeleteUnit,
} from "@/features/material/hooks/useUnits";
import { toast } from "@/shared/lib/toast";

const TYPE_LABELS: Record<string, string> = {
  BOOK: "책",
  LECTURE: "강의",
  WORKBOOK: "문제집",
  MOOC: "MOOC",
};

export function MaterialDetailPage() {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const materialId = Number(id);

  const { data: material, isLoading, isError } = useMaterial(materialId);
  const completeUnit = useCompleteUnit(materialId);
  const deleteUnitMutation = useDeleteUnit(materialId);
  const deleteMaterialMutation = useDeleteMaterial();

  const [addUnitsOpen, setAddUnitsOpen] = useState(false);
  const [editingUnit, setEditingUnit] = useState<UnitSummary | null>(null);
  const [deletingUnit, setDeletingUnit] = useState<UnitSummary | null>(null);
  const [editMaterialOpen, setEditMaterialOpen] = useState(false);
  const [deleteMaterialOpen, setDeleteMaterialOpen] = useState(false);
  const [completingUnitId, setCompletingUnitId] = useState<number | null>(null);

  const handleComplete = async (unit: UnitSummary) => {
    setCompletingUnitId(unit.id);
    try {
      const result = await completeUnit.mutateAsync(unit.id);
      toast(
        `${result.firstReview.scheduledDate}에 첫 복습이 예정되었어요.`,
        "success",
      );
    } finally {
      setCompletingUnitId(null);
    }
  };

  const handleDeleteUnit = async () => {
    if (!deletingUnit) return;
    await deleteUnitMutation.mutateAsync(deletingUnit.id);
    toast("단위가 삭제되었어요.", "success");
    setDeletingUnit(null);
  };

  const handleDeleteMaterial = async () => {
    await deleteMaterialMutation.mutateAsync(materialId);
    toast("자료가 삭제되었어요.", "success");
    navigate("/planner/materials", { replace: true });
  };

  if (isLoading) {
    return <p className="text-muted-foreground text-sm">불러오는 중...</p>;
  }

  if (isError || !material) {
    return (
      <div className="flex flex-col gap-4">
        <Link
          to="/planner/materials"
          className="text-muted-foreground hover:text-foreground inline-flex w-fit items-center gap-1 text-sm"
        >
          <ChevronLeft className="size-4" /> 뒤로
        </Link>
        <p className="text-destructive text-sm">
          학습 자료를 불러오지 못했어요.
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <Link
        to="/planner/materials"
        className="text-muted-foreground hover:text-foreground inline-flex w-fit items-center gap-1 text-sm"
      >
        <ChevronLeft className="size-4" /> 뒤로
      </Link>

      <header className="flex flex-col gap-3">
        <div className="flex items-start justify-between gap-3">
          <div className="flex flex-col gap-1">
            <h1 className="text-xl font-semibold">{material.title}</h1>
            <p className="text-muted-foreground text-xs">
              {TYPE_LABELS[material.type] ?? material.type}
              {material.status === "COMPLETED" && " · 완료"}
            </p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setEditMaterialOpen(true)}
          >
            <Settings2 className="size-3.5" /> 편집
          </Button>
        </div>
        <MaterialProgress
          completed={material.completedUnits}
          total={material.totalUnits}
        />
      </header>

      <section className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-medium">학습 단위</h2>
          <Button size="sm" onClick={() => setAddUnitsOpen(true)}>
            <Plus className="size-3.5" /> 단위 추가
          </Button>
        </div>

        {material.units.length === 0 ? (
          <p className="text-muted-foreground py-8 text-center text-sm">
            등록된 단위가 없습니다. 위 [단위 추가]로 시작해보세요.
          </p>
        ) : (
          <ul className="flex flex-col gap-2">
            {material.units.map((unit) => (
              <UnitItem
                key={unit.id}
                unit={unit}
                onComplete={handleComplete}
                onEdit={setEditingUnit}
                onDelete={setDeletingUnit}
                completing={completingUnitId === unit.id}
              />
            ))}
          </ul>
        )}
      </section>

      <AddUnitsDialog
        materialId={materialId}
        open={addUnitsOpen}
        onOpenChange={setAddUnitsOpen}
      />

      <EditUnitDialog
        materialId={materialId}
        unit={editingUnit}
        open={editingUnit !== null}
        onOpenChange={(open) => {
          if (!open) setEditingUnit(null);
        }}
      />

      <ConfirmDialog
        open={deletingUnit !== null}
        onOpenChange={(open) => {
          if (!open) setDeletingUnit(null);
        }}
        title="단위를 삭제할까요?"
        description={`"${deletingUnit?.title}" 단위가 삭제됩니다. 연결된 복습 일정도 함께 사라져요.`}
        confirmLabel="삭제"
        destructive
        onConfirm={handleDeleteUnit}
        pending={deleteUnitMutation.isPending}
      />

      <EditMaterialDialog
        material={material}
        open={editMaterialOpen}
        onOpenChange={setEditMaterialOpen}
        onRequestDelete={() => setDeleteMaterialOpen(true)}
      />

      <ConfirmDialog
        open={deleteMaterialOpen}
        onOpenChange={setDeleteMaterialOpen}
        title="자료를 삭제할까요?"
        description="자료와 함께 모든 단위 및 복습 일정이 삭제됩니다. 되돌릴 수 없어요."
        confirmLabel="삭제"
        destructive
        onConfirm={handleDeleteMaterial}
        pending={deleteMaterialMutation.isPending}
      />
    </div>
  );
}
