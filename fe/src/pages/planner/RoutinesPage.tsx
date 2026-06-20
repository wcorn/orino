import { Dialog } from "@base-ui/react/dialog";
import { Plus } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { DialogFooter } from "@/components/ui/dialog-footer";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import { Modal } from "@/components/ui/modal";
import { GoogleConnectButton } from "@/features/google/components/GoogleConnectButton";
import { useGoogleStatus } from "@/features/google/hooks/useGoogleStatus";
import type {
  RoutineCreateRequest,
  RoutineSeriesSummary,
} from "@/features/planner/api/routines";
import { RoutineFormDialog } from "@/features/planner/components/routine/RoutineFormDialog";
import { RoutineListItem } from "@/features/planner/components/routine/RoutineListItem";
import { useRoutineList } from "@/features/planner/hooks/useRoutineList";
import {
  useCreateRoutine,
  useDeleteRoutine,
  useUpdateRoutine,
} from "@/features/planner/hooks/useRoutineMutations";

function localToday(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

interface SectionProps {
  title: string;
  series: RoutineSeriesSummary[];
  onEdit: (s: RoutineSeriesSummary) => void;
  onDelete: (s: RoutineSeriesSummary) => void;
}

function RoutineSection({ title, series, onEdit, onDelete }: SectionProps) {
  if (series.length === 0) return null;
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-muted-foreground text-xs font-semibold">{title}</h2>
      <ul className="flex flex-col gap-2">
        {series.map((s) => (
          <RoutineListItem
            key={s.recurringEventId}
            series={s}
            onEdit={onEdit}
            onDelete={onDelete}
          />
        ))}
      </ul>
    </section>
  );
}

export function RoutinesPage() {
  const status = useGoogleStatus();
  const connected = status.data?.connected ?? false;
  const list = useRoutineList(connected);

  const createRoutine = useCreateRoutine();
  const updateRoutine = useUpdateRoutine();
  const deleteRoutine = useDeleteRoutine();

  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<RoutineSeriesSummary | null>(
    null,
  );
  const [deleteTarget, setDeleteTarget] = useState<RoutineSeriesSummary | null>(
    null,
  );

  const handleCreate = (values: RoutineCreateRequest) =>
    createRoutine.mutate(values, { onSuccess: () => setCreateOpen(false) });

  const handleUpdate = (values: RoutineCreateRequest) => {
    if (!editTarget) return;
    // scope=all 고정(전체 시리즈). 범위 선택 다이얼로그는 R4(#581)에서 추가.
    const { type: _type, color: _color, ...request } = values;
    void _type;
    void _color;
    updateRoutine.mutate(
      { eventId: editTarget.recurringEventId, request, scope: "all" },
      { onSuccess: () => setEditTarget(null) },
    );
  };

  const confirmDelete = () => {
    if (!deleteTarget) return;
    deleteRoutine.mutate(
      { eventId: deleteTarget.recurringEventId, scope: "all" },
      { onSuccess: () => setDeleteTarget(null) },
    );
  };

  const routines = list.data ?? [];
  const habits = routines.filter((r) => r.type === "habit");
  const schedules = routines.filter((r) => r.type === "schedule");

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-xl font-semibold">루틴</h1>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="size-4" /> 새 루틴
        </Button>
      </div>

      {status.isLoading ? (
        <LoadingText />
      ) : !connected ? (
        <EmptyState>
          <p className="text-muted-foreground text-sm">
            Google 연결이 필요합니다.
          </p>
          <GoogleConnectButton />
        </EmptyState>
      ) : list.isLoading ? (
        <LoadingText />
      ) : list.isError ? (
        <EmptyState>
          <p className="text-muted-foreground text-sm">
            루틴을 불러오지 못했습니다.
          </p>
          <Button variant="outline" onClick={() => list.refetch()}>
            다시 시도
          </Button>
        </EmptyState>
      ) : routines.length === 0 ? (
        <EmptyState>
          <p className="text-muted-foreground text-sm">아직 루틴이 없습니다.</p>
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="size-4" /> 새 루틴
          </Button>
        </EmptyState>
      ) : (
        <div className="flex flex-col gap-5">
          <RoutineSection
            title="습관"
            series={habits}
            onEdit={setEditTarget}
            onDelete={setDeleteTarget}
          />
          <RoutineSection
            title="고정 일정"
            series={schedules}
            onEdit={setEditTarget}
            onDelete={setDeleteTarget}
          />
        </div>
      )}

      <RoutineFormDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        googleConnected={connected}
        defaultDate={localToday()}
        pending={createRoutine.isPending}
        onSubmit={handleCreate}
      />

      <RoutineFormDialog
        open={!!editTarget}
        onOpenChange={(open) => !open && setEditTarget(null)}
        googleConnected={connected}
        defaultDate={localToday()}
        series={editTarget ?? undefined}
        pending={updateRoutine.isPending}
        onSubmit={handleUpdate}
      />

      <Modal
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        className="max-w-sm"
      >
        <Dialog.Title className="text-base font-semibold">
          루틴 삭제
        </Dialog.Title>
        <p className="text-muted-foreground mt-2 text-sm">
          ‘{deleteTarget?.title}’ 루틴을 삭제할까요?
        </p>
        <DialogFooter>
          <Dialog.Close
            render={
              <Button variant="ghost" type="button">
                취소
              </Button>
            }
          />
          <Button
            variant="destructive"
            onClick={confirmDelete}
            disabled={deleteRoutine.isPending}
          >
            삭제
          </Button>
        </DialogFooter>
      </Modal>
    </div>
  );
}
