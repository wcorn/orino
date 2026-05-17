import { Plus } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { AddMaterialDialog } from "@/features/material/components/AddMaterialDialog";
import { EmptyMaterialState } from "@/features/material/components/EmptyMaterialState";
import { MaterialCard } from "@/features/material/components/MaterialCard";
import { useMaterials } from "@/features/material/hooks/useMaterials";

export function MaterialListPage() {
  const navigate = useNavigate();
  const { data: materials, isLoading } = useMaterials("ACTIVE");
  const [dialogOpen, setDialogOpen] = useState(false);

  const handleCreated = (materialId: number) => {
    navigate(`/planner/materials/${materialId}?tab=note`);
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-xl font-semibold">학습 자료</h1>
        <Button onClick={() => setDialogOpen(true)}>
          <Plus className="size-4" /> 자료 추가
        </Button>
      </div>

      {isLoading ? (
        <p className="text-muted-foreground text-sm">불러오는 중...</p>
      ) : !materials || materials.length === 0 ? (
        <EmptyMaterialState onAdd={() => setDialogOpen(true)} />
      ) : (
        <ul className="flex flex-col gap-3">
          {materials.map((material) => (
            <li key={material.id}>
              <MaterialCard material={material} />
            </li>
          ))}
        </ul>
      )}

      <AddMaterialDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        onCreated={handleCreated}
      />
    </div>
  );
}
