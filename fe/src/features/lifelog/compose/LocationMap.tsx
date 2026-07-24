import "leaflet/dist/leaflet.css";

import L from "leaflet";
import { useEffect } from "react";
import {
  MapContainer,
  Marker,
  TileLayer,
  useMap,
  useMapEvents,
} from "react-leaflet";

export interface LatLng {
  lat: number;
  lng: number;
}

/** 서울 시청 — 위치 미지정 시 기본 중심. */
const DEFAULT_CENTER: LatLng = { lat: 37.5665, lng: 126.978 };

// 이미지 에셋 없이 쓰는 divIcon 핀(leaflet 기본 마커 이미지 로딩 이슈 회피).
const pinIcon = L.divIcon({
  className: "lifelog-pin",
  html: '<div style="font-size:24px;line-height:1">📍</div>',
  iconSize: [24, 24],
  iconAnchor: [12, 24],
});

function ClickHandler({
  onPick,
}: {
  onPick: (lat: number, lng: number) => void;
}) {
  useMapEvents({
    click(e) {
      onPick(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

/** value가 바뀌면(검색 선택 등) 지도를 그 위치로 이동시킨다. */
function Recenter({ center }: { center: LatLng | null }) {
  const map = useMap();
  useEffect(() => {
    if (center) map.setView([center.lat, center.lng], 14);
  }, [center, map]);
  return null;
}

interface LocationMapProps {
  value: LatLng | null;
  onPick: (lat: number, lng: number) => void;
}

/** OSM 타일 지도. 클릭으로 핀을 찍고, value가 있으면 마커를 표시한다. (브라우저 전용) */
export function LocationMap({ value, onPick }: LocationMapProps) {
  const center = value ?? DEFAULT_CENTER;
  return (
    <MapContainer
      center={[center.lat, center.lng]}
      zoom={value ? 14 : 11}
      scrollWheelZoom
      style={{ height: 300, width: "100%", borderRadius: 8 }}
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <ClickHandler onPick={onPick} />
      <Recenter center={value} />
      {value && <Marker position={[value.lat, value.lng]} icon={pinIcon} />}
    </MapContainer>
  );
}
