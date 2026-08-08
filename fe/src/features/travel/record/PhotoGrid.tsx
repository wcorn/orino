import { ImagePlus, RotateCcw, X } from "lucide-react";
import { useId, useRef, useState } from "react";

import {
  type ActivityPhoto,
  deletePhoto as deletePhotoRequest,
} from "@/features/travel/api/photos";
import { PhotoViewer } from "@/features/travel/record/PhotoViewer";
import {
  MAX_PHOTOS,
  usePhotoUpload,
} from "@/features/travel/record/usePhotoUpload";
import { toast } from "@/shared/lib/toast";

interface PhotoGridProps {
  activityId: number;
  photos: ActivityPhoto[];
  onChange: (photos: ActivityPhoto[]) => void;
  online: boolean;
}

/**
 * 썸네일 그리드 + 추가 버튼(§S-07).
 *
 * <p>업로드 중인 장은 로컬 미리보기로 자리를 잡아 둔다 — 고른 사진이 잠깐 사라졌다 나타나면
 * 눌린 건지 아닌지 알 수 없다. 실패한 장은 남겨 다시 고르게 한다.
 */
export function PhotoGrid({
  activityId,
  photos,
  onChange,
  online,
}: PhotoGridProps) {
  const inputId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [viewerIndex, setViewerIndex] = useState<number | null>(null);

  const { pending, add, dismiss } = usePhotoUpload(
    activityId,
    photos.length,
    onChange,
  );

  const full = photos.length + pending.length >= MAX_PHOTOS;

  const remove = async (photo: ActivityPhoto) => {
    // 낙관적으로 지운다 — 실패하면 되돌린다. 사진 삭제는 즉시 반응해야 한다.
    const before = photos;
    onChange(photos.filter((p) => p.id !== photo.id));
    try {
      await deletePhotoRequest(photo.id);
    } catch {
      onChange(before);
      toast("사진을 지우지 못했어요.", "error");
    }
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="grid grid-cols-4 gap-1.5">
        {photos.map((photo, index) => (
          <div key={photo.id} className="relative aspect-square">
            <button
              type="button"
              className="size-full overflow-hidden rounded-lg"
              aria-label={`사진 ${index + 1} 크게 보기`}
              onClick={() => setViewerIndex(index)}
            >
              {/* 썸네일이 없으면 원본을 줄여 쓴다 — 썸네일만 실패할 수 있다. */}
              <img
                src={photo.thumbUrl ?? photo.url}
                alt=""
                loading="lazy"
                className="size-full object-cover"
              />
            </button>
            {online && (
              <button
                type="button"
                aria-label={`사진 ${index + 1} 삭제`}
                onClick={() => remove(photo)}
                className="absolute top-1 right-1 rounded-full bg-black/60 p-1 text-white"
              >
                <X className="size-3" />
              </button>
            )}
          </div>
        ))}

        {pending.map((item) => (
          <div
            key={item.key}
            className="relative aspect-square overflow-hidden rounded-lg"
          >
            <img
              src={item.previewUrl}
              alt=""
              className="size-full object-cover opacity-40"
            />
            {item.status === "uploading" ? (
              <span className="absolute inset-0 grid place-items-center text-[11px] text-white">
                올리는 중…
              </span>
            ) : (
              <button
                type="button"
                aria-label="실패한 사진 지우기"
                title={item.message}
                onClick={() => dismiss(item.key)}
                className="bg-destructive/70 absolute inset-0 grid place-items-center text-[11px] text-white"
              >
                <RotateCcw className="size-4" />
              </button>
            )}
          </div>
        ))}

        {!full && (
          <label
            htmlFor={inputId}
            className={`border-border text-muted-foreground grid aspect-square place-items-center rounded-lg border border-dashed ${
              online ? "cursor-pointer" : "opacity-50"
            }`}
            aria-label="사진 추가"
          >
            <ImagePlus className="size-5" />
          </label>
        )}
      </div>

      <input
        id={inputId}
        ref={inputRef}
        type="file"
        accept="image/*"
        multiple
        disabled={!online}
        className="sr-only"
        onChange={(e) => {
          const files = e.target.files;
          if (files) void add(files);
          // 같은 파일을 다시 고를 수 있게 비운다.
          e.target.value = "";
        }}
      />

      {full && (
        <p className="text-muted-foreground text-xs">
          사진은 {MAX_PHOTOS}장까지 올릴 수 있어요.
        </p>
      )}

      {viewerIndex !== null && (
        <PhotoViewer
          photos={photos}
          startIndex={viewerIndex}
          onClose={() => setViewerIndex(null)}
        />
      )}
    </div>
  );
}
