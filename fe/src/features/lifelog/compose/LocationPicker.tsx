import { MapPin } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/modal";

import { useReverseGeocode } from "../hooks/useGeocode";
import { LocationMap } from "./LocationMap";
import { LocationSearch } from "./LocationSearch";

export interface LocationValue {
  lat: number;
  lng: number;
  placeName: string | null;
}

interface LocationPickerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  value: LocationValue | null;
  /** 확인 시 선택 위치(또는 지우기 시 null)를 돌려준다. */
  onConfirm: (location: LocationValue | null) => void;
}

/** 지도 클릭·검색으로 위치를 지정하는 모달. 좌표가 정해지면 역지오코딩으로 장소명을 채운다. */
export function LocationPicker({
  open,
  onOpenChange,
  value,
  onConfirm,
}: LocationPickerProps) {
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(
    null,
  );
  const [placeName, setPlaceName] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setCoords(value ? { lat: value.lat, lng: value.lng } : null);
    setPlaceName(value?.placeName ?? null);
  }, [open, value]);

  // 지도 클릭으로 placeName을 비운 뒤 좌표가 바뀌면 역지오코딩으로 채운다.
  const { data: reverse } = useReverseGeocode(coords);
  useEffect(() => {
    if (reverse && placeName == null) {
      setPlaceName(reverse.placeName);
    }
  }, [reverse, placeName]);

  const pickFromMap = (lat: number, lng: number) => {
    setCoords({ lat, lng });
    setPlaceName(null); // 역지오코딩이 채우도록
  };

  return (
    <Modal open={open} onOpenChange={onOpenChange} title="위치 선택" size="lg">
      <div className="mt-4 flex flex-col gap-3">
        <LocationSearch
          onSelect={(place) => {
            setCoords({ lat: place.lat, lng: place.lng });
            setPlaceName(place.placeName);
          }}
        />
        <LocationMap value={coords} onPick={pickFromMap} />
        <p className="text-muted-foreground flex items-center gap-1 text-sm">
          <MapPin className="size-4" />
          {coords
            ? (placeName ?? "장소 확인 중...")
            : "지도를 클릭하거나 검색해 위치를 지정하세요"}
        </p>
      </div>

      <Modal.Footer>
        <Button
          type="button"
          variant="ghost"
          onClick={() => {
            onConfirm(null);
            onOpenChange(false);
          }}
        >
          지우기
        </Button>
        <div className="ml-auto flex gap-2">
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
          >
            취소
          </Button>
          <Button
            type="button"
            disabled={!coords}
            onClick={() => {
              if (!coords) return;
              onConfirm({ ...coords, placeName });
              onOpenChange(false);
            }}
          >
            확인
          </Button>
        </div>
      </Modal.Footer>
    </Modal>
  );
}
