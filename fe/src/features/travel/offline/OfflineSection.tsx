import { useCallback, useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import {
  clearTravelCache,
  countCachedResponses,
  estimateStorage,
  formatBytes,
  type StorageUsage,
} from "@/features/travel/offline/storage";
import { toast } from "@/shared/lib/toast";

interface OfflineState {
  storage: StorageUsage | null;
  count: number | null;
}

/**
 * S-09 <b>오프라인</b> 섹션 — 저장 용량과 초기화.
 *
 * <p>캐시는 이미 돌고 있지만 <b>사용자가 그 존재를 볼 수 없다</b>. 얼마나 쌓였는지, 비행기
 * 모드에서 뭐가 보일지, 이상하면 어떻게 지우는지 — 이 섹션이 그 셋을 답한다.
 */
export function OfflineSection() {
  const [state, setState] = useState<OfflineState | null>(null);
  const [clearing, setClearing] = useState(false);

  const refresh = useCallback(async () => {
    const [storage, count] = await Promise.all([
      estimateStorage(),
      countCachedResponses(),
    ]);
    setState({ storage, count });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const clear = async () => {
    setClearing(true);
    try {
      const had = await clearTravelCache();
      await refresh();
      toast(
        had ? "오프라인 데이터를 비웠어요." : "비울 데이터가 없어요.",
        "success",
      );
    } catch {
      toast("비우지 못했어요.", "error");
    } finally {
      setClearing(false);
    }
  };

  return (
    <section className="flex flex-col gap-3 border-t pt-5">
      <h2 className="text-caption text-muted-foreground font-semibold">
        오프라인
      </h2>

      {state === null ? (
        <LoadingText />
      ) : (
        <>
          <div className="flex items-center gap-3">
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium">저장된 데이터</p>
              <p className="text-muted-foreground text-xs">{describe(state)}</p>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={clear}
              disabled={clearing || state.count === 0}
            >
              비우기
            </Button>
          </div>

          {/* 무엇이 남는지 밝힌다 — 비운 뒤 앱이 안 열릴까 걱정하게 두지 않는다. */}
          <p className="text-muted-foreground text-xs">
            비우면 저장된 일정이 사라지고, 온라인에서 다시 열 때 채워집니다. 앱
            자체는 남아 있어 오프라인에서도 계속 열립니다.
          </p>
        </>
      )}
    </section>
  );
}

/**
 * 용량과 건수를 한 줄로 만든다.
 *
 * <p>지원하지 않는 브라우저에서는 <b>모른다고 말한다</b> — 0으로 꾸미면 "아무것도 안
 * 쌓였다"로 읽혀 정반대의 오해를 만든다.
 */
function describe({ storage, count }: OfflineState): string {
  const parts: string[] = [];
  if (count !== null) {
    parts.push(count === 0 ? "저장된 일정 없음" : `일정 ${count}건`);
  }
  if (storage !== null) {
    parts.push(formatBytes(storage.usage));
  }
  return parts.length > 0
    ? parts.join(" · ")
    : "이 브라우저에서는 알 수 없어요";
}
