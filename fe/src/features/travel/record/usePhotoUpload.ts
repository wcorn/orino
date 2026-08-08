import { useState } from "react";

import {
  type ActivityPhoto,
  createPhotoUploadUrl,
  type PhotoMeta,
  putToPresigned,
  registerPhotos,
} from "@/features/travel/api/photos";
import {
  MAX_FILE_BYTES,
  processPhoto,
} from "@/features/travel/record/processPhoto";

/** 일정당 사진 상한(§2.5). 서버 검증과 같은 값이다. */
export const MAX_PHOTOS = 10;

export interface PendingPhoto {
  key: string;
  /** 로컬 미리보기(objectURL). 업로드가 끝나면 서버 URL로 갈린다. */
  previewUrl: string;
  status: "uploading" | "error";
  /** 실패 사유. 재시도할지 판단은 사용자가 한다. */
  message?: string;
}

interface UsePhotoUploadResult {
  pending: PendingPhoto[];
  /** 고른 파일을 처리·업로드하고 등록까지 마친다. */
  add: (files: FileList | File[]) => Promise<void>;
  /** 실패한 장을 목록에서 치운다. */
  dismiss: (key: string) => void;
}

/**
 * 사진 업로드.
 *
 * <p><b>성공한 장만 등록한다.</b> 한 장이 실패했다고 이미 올라간 아홉 장을 버리지 않는다 —
 * 현지 회선에서 열 장이 다 성공하는 일이 드물다.
 *
 * <p>실패한 장은 목록에 남겨 사용자가 다시 고를 수 있게 한다. 조용히 사라지면 몇 장이
 * 빠졌는지도 모른다.
 */
export function usePhotoUpload(
  activityId: number,
  currentCount: number,
  onUploaded: (photos: ActivityPhoto[]) => void,
): UsePhotoUploadResult {
  const [pending, setPending] = useState<PendingPhoto[]>([]);

  const dismiss = (key: string) =>
    setPending((prev) => {
      const target = prev.find((p) => p.key === key);
      if (target) URL.revokeObjectURL(target.previewUrl);
      return prev.filter((p) => p.key !== key);
    });

  const add = async (files: FileList | File[]) => {
    const picked = Array.from(files);
    if (picked.length === 0) return;

    // 상한을 넘는 만큼은 아예 시작하지 않는다 — 올린 뒤 서버가 거절하면 통신만 버린다.
    const room = MAX_PHOTOS - currentCount - pending.length;
    const accepted = picked.slice(0, Math.max(0, room));

    const entries = accepted.map((file, i) => ({
      file,
      key: `${file.name}-${i}-${file.size}`,
      previewUrl: URL.createObjectURL(file),
    }));
    setPending((prev) => [
      ...prev,
      ...entries.map((e) => ({
        key: e.key,
        previewUrl: e.previewUrl,
        status: "uploading" as const,
      })),
    ]);

    const uploaded: PhotoMeta[] = [];
    for (const entry of entries) {
      try {
        if (entry.file.size > MAX_FILE_BYTES) {
          throw new Error("사진이 너무 커요 (15MB까지)");
        }
        const processed = await processPhoto(entry.file);

        const original = await createPhotoUploadUrl(activityId, "ORIGINAL");
        await putToPresigned(original.uploadUrl, processed.originalBlob);

        // 썸네일만 실패해도 사진은 살린다 — 원본을 줄여 보여주면 된다.
        let thumbKey: string | null = null;
        try {
          const thumb = await createPhotoUploadUrl(activityId, "THUMB");
          await putToPresigned(thumb.uploadUrl, processed.thumbBlob);
          thumbKey = thumb.objectKey;
        } catch {
          thumbKey = null;
        }

        uploaded.push({
          objectKey: original.objectKey,
          thumbKey,
          width: processed.width,
          height: processed.height,
        });
        URL.revokeObjectURL(entry.previewUrl);
        setPending((prev) => prev.filter((p) => p.key !== entry.key));
      } catch (error) {
        setPending((prev) =>
          prev.map((p) =>
            p.key === entry.key
              ? {
                  ...p,
                  status: "error" as const,
                  message:
                    error instanceof Error && error.message
                      ? error.message
                      : "올리지 못했어요",
                }
              : p,
          ),
        );
      }
    }

    if (uploaded.length > 0) {
      onUploaded(await registerPhotos(activityId, uploaded));
    }
  };

  return { pending, add, dismiss };
}
