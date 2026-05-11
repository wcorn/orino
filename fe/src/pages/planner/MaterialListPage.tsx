import { Plus } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { AddMaterialDialog } from "@/features/material/components/AddMaterialDialog";
import { EmptyMaterialState } from "@/features/material/components/EmptyMaterialState";
import { MaterialCard } from "@/features/material/components/MaterialCard";
import { useMaterials } from "@/features/material/hooks/useMaterials";

export function MaterialListPage() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const { data: materials, isLoading, isError } = useMaterials();

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">학습 자료</h1>
        <Button onClick={() => setDialogOpen(true)}>
          <Plus className="size-4" />
          자료 추가
        </Button>
      </div>

      {isLoading && (
        <p className="text-muted-foreground text-sm">불러오는 중...</p>
      )}

      {isError && (
        <p className="text-destructive text-sm">
          학습 자료를 불러오지 못했어요. 잠시 후 다시 시도해주세요.
        </p>
      )}

      {!isLoading && !isError && materials && materials.length === 0 && (
        <EmptyMaterialState onAdd={() => setDialogOpen(true)} />
      )}

      {!isLoading && !isError && materials && materials.length > 0 && (
        <ul className="flex flex-col gap-3">
          {materials.map((material) => (
            <li key={material.id}>
              <MaterialCard material={material} />
            </li>
          ))}
        </ul>
      )}

      <AddMaterialDialog open={dialogOpen} onOpenChange={setDialogOpen} />
    </div>
  );
}
