import { MapPin, X } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/modal";
import { Textarea } from "@/components/ui/textarea";

import type { MomentCard, Mood, PhotoRequest } from "../api/types";
import { useReverseGeocode } from "../hooks/useGeocode";
import { useCreateMoment, useUpdateMoment } from "../hooks/useMomentMutations";
import { isoToLocalInput, localInputToIso } from "../lib/datetime";
import { photoToRequest } from "../lib/photoKey";
import { LocationPicker, LocationValue } from "./LocationPicker";
import { MoodPicker } from "./MoodPicker";
import { InitialPhoto, PhotoUploader } from "./PhotoUploader";
import { TagInput } from "./TagInput";

interface MomentEditorProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 있으면 수정 모드. */
  moment?: MomentCard;
}

/** 기록 작성/수정 모달. 사진(EXIF·썸네일 업로드)·본문·발생시각·위치·기분·태그. */
export function MomentEditor({
  open,
  onOpenChange,
  moment,
}: MomentEditorProps) {
  const isEdit = Boolean(moment);
  const createMutation = useCreateMoment();
  const updateMutation = useUpdateMoment();
  const pending = createMutation.isPending || updateMutation.isPending;

  const [photos, setPhotos] = useState<PhotoRequest[]>([]);
  const [uploading, setUploading] = useState(0);
  const [body, setBody] = useState("");
  const [mood, setMood] = useState<Mood | null>(null);
  const [tags, setTags] = useState<string[]>([]);
  const [occurredAt, setOccurredAt] = useState("");
  const [occurredTouched, setOccurredTouched] = useState(false);
  const [location, setLocation] = useState<LocationValue | null>(null);
  const [locationOpen, setLocationOpen] = useState(false);

  // 열릴 때 초기화(수정 모드는 기존 값).
  useEffect(() => {
    if (!open) return;
    setBody(moment?.body ?? "");
    setMood(moment?.mood ?? null);
    setTags(moment?.tags ?? []);
    setOccurredAt(isoToLocalInput(moment?.occurredAt));
    setOccurredTouched(false);
    setLocation(
      moment?.lat != null && moment?.lng != null
        ? { lat: moment.lat, lng: moment.lng, placeName: moment.placeName }
        : null,
    );
    setPhotos([]);
    setUploading(0);
  }, [open, moment]);

  // 새 기록에서 첫 사진 EXIF로 발생시각·위치 자동 채움(사용자가 손대지 않았을 때만).
  useEffect(() => {
    if (isEdit || photos.length === 0) return;
    const first = photos[0];
    if (!occurredTouched && first.exifTakenAt) {
      setOccurredAt(isoToLocalInput(first.exifTakenAt));
    }
    if (!location && first.exifLat != null && first.exifLng != null) {
      setLocation({ lat: first.exifLat, lng: first.exifLng, placeName: null });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [photos]);

  // 좌표만 있고 장소명이 없으면(EXIF 자동채움) 역지오코딩으로 장소명을 채운다.
  const { data: autoReverse } = useReverseGeocode(
    location && location.placeName == null
      ? { lat: location.lat, lng: location.lng }
      : null,
  );
  useEffect(() => {
    if (autoReverse) {
      setLocation((prev) =>
        prev && prev.placeName == null
          ? { ...prev, placeName: autoReverse.placeName }
          : prev,
      );
    }
  }, [autoReverse]);

  const initialPhotos: InitialPhoto[] = (moment?.photos ?? []).map((p) => ({
    previewUrl: p.thumbUrl ?? p.url,
    request: photoToRequest(p),
  }));

  const canSubmit =
    uploading === 0 &&
    !pending &&
    (body.trim().length > 0 || photos.length > 0);

  const submit = () => {
    const request = {
      occurredAt: localInputToIso(occurredAt),
      body: body.trim() || null,
      mood,
      lat: location?.lat ?? null,
      lng: location?.lng ?? null,
      placeName: location?.placeName ?? null,
      tags,
      photos,
    };
    const onSuccess = () => onOpenChange(false);
    if (moment) {
      updateMutation.mutate({ id: moment.id, request }, { onSuccess });
    } else {
      createMutation.mutate(request, { onSuccess });
    }
  };

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title={isEdit ? "기록 수정" : "기록"}
      size="lg"
    >
      <div className="mt-4 flex flex-col gap-4">
        <PhotoUploader
          key={moment?.id ?? "new"}
          initial={initialPhotos}
          onChange={setPhotos}
          onUploadingChange={setUploading}
        />

        <Textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder="지금 이 순간을 기록하세요"
          rows={3}
          aria-label="본문"
        />

        <label className="flex flex-col gap-1 text-sm">
          <span className="text-muted-foreground text-xs">발생 시각</span>
          <input
            type="datetime-local"
            value={occurredAt}
            onChange={(e) => {
              setOccurredAt(e.target.value);
              setOccurredTouched(true);
            }}
            className="border-border bg-background h-9 rounded-md border px-3 text-sm"
            aria-label="발생 시각"
          />
        </label>

        <div>
          <span className="text-muted-foreground mb-1.5 block text-xs">
            위치
          </span>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setLocationOpen(true)}
            >
              <MapPin />
              {location
                ? (location.placeName ??
                  `${location.lat.toFixed(4)}, ${location.lng.toFixed(4)}`)
                : "위치 추가"}
            </Button>
            {location && (
              <button
                type="button"
                aria-label="위치 지우기"
                onClick={() => setLocation(null)}
                className="text-muted-foreground hover:text-foreground"
              >
                <X className="size-4" />
              </button>
            )}
          </div>
        </div>

        <div>
          <span className="text-muted-foreground mb-1.5 block text-xs">
            기분
          </span>
          <MoodPicker value={mood} onChange={setMood} />
        </div>

        <div>
          <span className="text-muted-foreground mb-1.5 block text-xs">
            태그
          </span>
          <TagInput tags={tags} onChange={setTags} />
        </div>
      </div>

      <Modal.Footer
        onSubmit={submit}
        submitLabel={isEdit ? "저장" : "기록"}
        pending={pending}
        pendingLabel={isEdit ? "저장 중..." : "기록 중..."}
        submitDisabled={!canSubmit}
      />

      <LocationPicker
        open={locationOpen}
        onOpenChange={setLocationOpen}
        value={location}
        onConfirm={setLocation}
      />
    </Modal>
  );
}
