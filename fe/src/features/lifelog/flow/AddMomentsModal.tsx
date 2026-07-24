import { ImageIcon } from "lucide-react";
import { useEffect, useState } from "react";

import { Checkbox } from "@/components/ui/checkbox";
import { Modal } from "@/components/ui/modal";

import { useFeed } from "../hooks/useFeed";
import { useAddMoments } from "../hooks/useFlowMutations";
import { formatMomentTime } from "../lib/datetime";

interface AddMomentsModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  flowId: number;
  /** 이미 담긴 기록 id — 목록에서 제외한다. */
  existingIds: number[];
}

/** 기록을 흐름에 담기 — 최근 기록에서 체크박스로 선택해 추가한다. */
export function AddMomentsModal({
  open,
  onOpenChange,
  flowId,
  existingIds,
}: AddMomentsModalProps) {
  const { data } = useFeed();
  const addMutation = useAddMoments(flowId);
  const [selected, setSelected] = useState<Set<number>>(new Set());

  useEffect(() => {
    if (open) setSelected(new Set());
  }, [open]);

  const candidates = (data?.pages.flatMap((page) => page.items) ?? []).filter(
    (m) => !existingIds.includes(m.id),
  );

  const toggle = (id: number) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const submit = () => {
    if (selected.size === 0) return;
    addMutation.mutate([...selected], {
      onSuccess: () => onOpenChange(false),
    });
  };

  return (
    <Modal open={open} onOpenChange={onOpenChange} title="기록 담기">
      <div className="mt-4 max-h-96 overflow-y-auto">
        {candidates.length === 0 ? (
          <p className="text-muted-foreground py-8 text-center text-sm">
            담을 수 있는 기록이 없어요.
          </p>
        ) : (
          <ul className="flex flex-col gap-1">
            {candidates.map((moment) => (
              <li key={moment.id}>
                <label className="hover:bg-muted flex cursor-pointer items-center gap-3 rounded-lg p-2">
                  <Checkbox
                    checked={selected.has(moment.id)}
                    onChange={() => toggle(moment.id)}
                    aria-label={`기록 ${moment.id} 선택`}
                  />
                  <div className="bg-muted text-muted-foreground flex size-10 shrink-0 items-center justify-center overflow-hidden rounded">
                    {moment.photos[0] ? (
                      <img
                        src={moment.photos[0].thumbUrl ?? moment.photos[0].url}
                        alt=""
                        className="size-full object-cover"
                      />
                    ) : (
                      <ImageIcon className="size-4" />
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="text-muted-foreground text-xs">
                      {formatMomentTime(moment.occurredAt)}
                    </p>
                    <p className="truncate text-sm">
                      {moment.body ?? "(사진)"}
                    </p>
                  </div>
                </label>
              </li>
            ))}
          </ul>
        )}
      </div>
      <Modal.Footer
        onSubmit={submit}
        submitLabel={selected.size > 0 ? `${selected.size}개 담기` : "담기"}
        pending={addMutation.isPending}
        pendingLabel="담는 중..."
        submitDisabled={selected.size === 0 || addMutation.isPending}
      />
    </Modal>
  );
}
