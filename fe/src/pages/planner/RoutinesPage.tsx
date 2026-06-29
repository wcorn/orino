import { Plus } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import { GoogleConnectButton } from "@/features/google/components/GoogleConnectButton";
import { useGoogleStatus } from "@/features/google/hooks/useGoogleStatus";
import type {
  RoutineCreateRequest,
  RoutineEditRequest,
  RoutineScope,
  RoutineSeriesSummary,
} from "@/features/planner/api/routines";
import { RoutineFormDialog } from "@/features/planner/components/routine/RoutineFormDialog";
import { RoutineListItem } from "@/features/planner/components/routine/RoutineListItem";
import { RoutineScopeDialog } from "@/features/planner/components/routine/RoutineScopeDialog";
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
  const [pendingEdit, setPendingEdit] = useState<{
    series: RoutineSeriesSummary;
    request: RoutineEditRequest;
  } | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<RoutineSeriesSummary | null>(
    null,
  );

  const handleCreate = (values: RoutineCreateRequest) =>
    createRoutine.mutate(values, { onSuccess: () => setCreateOpen(false) });

  // 폼 저장 → 범위 선택 다이얼로그로 넘긴다(종류·색상은 편집 대상이 아님).
  const handleEditSubmit = (values: RoutineCreateRequest) => {
    if (!editTarget) return;
    const { type: _type, color: _color, ...request } = values;
    void _type;
    void _color;
    setPendingEdit({ series: editTarget, request });
    setEditTarget(null);
  };

  const confirmEdit = (scope: RoutineScope) => {
    if (!pendingEdit) return;
    const instanceDate = pendingEdit.series.start.slice(0, 10);
    updateRoutine.mutate(
      {
        eventId: pendingEdit.series.recurringEventId,
        request: pendingEdit.request,
        scope,
        instanceDate: scope === "all" ? undefined : instanceDate,
      },
      { onSuccess: () => setPendingEdit(null) },
    );
  };

  const confirmDelete = (scope: RoutineScope) => {
    if (!deleteTarget) return;
    const instanceDate = deleteTarget.start.slice(0, 10);
    deleteRoutine.mutate(
      {
        eventId: deleteTarget.recurringEventId,
        scope,
        instanceDate: scope === "all" ? undefined : instanceDate,
      },
      { onSuccess: () => setDeleteTarget(null) },
    );
  };

  const routines = list.data ?? [];
  const habits = routines.filter((r) => r.type === "habit");
  const schedules = routines.filter((r) => r.type === "schedule");

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="루틴"
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="size-4" /> 새 루틴
          </Button>
        }
      />

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
        onSubmit={handleEditSubmit}
      />

      <RoutineScopeDialog
        open={!!pendingEdit}
        onOpenChange={(open) => !open && setPendingEdit(null)}
        mode="edit"
        instanceDate={pendingEdit?.series.start.slice(0, 10)}
        defaultScope="all"
        pending={updateRoutine.isPending}
        onConfirm={confirmEdit}
      />

      <RoutineScopeDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        mode="delete"
        instanceDate={deleteTarget?.start.slice(0, 10)}
        defaultScope="all"
        pending={deleteRoutine.isPending}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
