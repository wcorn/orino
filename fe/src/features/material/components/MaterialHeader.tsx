import { Menu } from "@base-ui/react/menu";
import { MoreHorizontal, Pencil, Trash2 } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";

import type { Material } from "../api/materials";
import { useDeleteMaterial } from "../hooks/useDeleteMaterial";
import { MATERIAL_TYPE_ICONS, MATERIAL_TYPE_LABELS } from "../utils";
import { EditMaterialDialog } from "./EditMaterialDialog";

interface Props {
  material: Material;
}

export function MaterialHeader({ material }: Props) {
  const navigate = useNavigate();
  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const deleteMutation = useDeleteMaterial();

  const handleDelete = () => {
    deleteMutation.mutate(material.id, {
      onSuccess: () => {
        setDeleteOpen(false);
        navigate("/planner/materials", { replace: true });
      },
    });
  };

  return (
    <div className="flex items-start gap-3">
      <span aria-hidden className="text-3xl leading-none">
        {MATERIAL_TYPE_ICONS[material.type]}
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <h1 className="truncate text-xl font-semibold">{material.title}</h1>
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

      <Menu.Root>
        <Menu.Trigger
          render={
            <Button variant="ghost" size="icon-sm" aria-label="자료 메뉴">
              <MoreHorizontal className="size-4" />
            </Button>
          }
        />
        <Menu.Portal>
          <Menu.Positioner sideOffset={4} align="end" className="z-50">
            <Menu.Popup className="bg-popover text-popover-foreground min-w-32 rounded-md border p-1 shadow-md">
              <Menu.Item
                onClick={() => setEditOpen(true)}
                className="data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none"
              >
                <Pencil className="size-3.5" /> 편집
              </Menu.Item>
              <Menu.Item
                onClick={() => setDeleteOpen(true)}
                className="text-destructive data-[highlighted]:bg-destructive/10 flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none"
              >
                <Trash2 className="size-3.5" /> 삭제
              </Menu.Item>
            </Menu.Popup>
          </Menu.Positioner>
        </Menu.Portal>
      </Menu.Root>

      <EditMaterialDialog
        material={material}
        open={editOpen}
        onOpenChange={setEditOpen}
      />
      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="자료를 삭제할까요?"
        description={
          <>
            자료, 노트, 카드 {material.flashcardCount}장과 복습 일정이 모두
            삭제됩니다.
            <br />이 작업은 되돌릴 수 없어요.
          </>
        }
        confirmLabel="삭제"
        destructive
        onConfirm={handleDelete}
        pending={deleteMutation.isPending}
      />
    </div>
  );
}
