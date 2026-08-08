import "leaflet/dist/leaflet.css";

import L from "leaflet";
import { useEffect } from "react";
import {
  MapContainer,
  Marker,
  Polyline,
  TileLayer,
  useMap,
} from "react-leaflet";

import type { MappedActivity } from "./toMapped";

function numberIcon(order: number, selected: boolean) {
  const size = selected ? 32 : 26;
  return L.divIcon({
    className: "travel-day-pin",
    html:
      `<div style="display:flex;align-items:center;justify-content:center;` +
      `width:${size}px;height:${size}px;border-radius:9999px;` +
      `background:var(--primary);color:var(--primary-foreground);` +
      `font-size:12px;font-weight:600;border:2px solid var(--card);` +
      `box-shadow:0 1px 3px rgba(0,0,0,.4)">${order}</div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  });
}

/** 핀이 전부 들어오게 범위를 맞춘다. */
function FitBounds({ points }: { points: [number, number][] }) {
  const map = useMap();
  useEffect(() => {
    if (points.length === 1) {
      map.setView(points[0], 15);
    } else if (points.length > 1) {
      map.fitBounds(points, { padding: [24, 24] });
    }
  }, [points, map]);
  return null;
}

interface TripDayMapProps {
  mapped: MappedActivity[];
  selectedId: number | null;
  onSelect: (activityId: number) => void;
}

/**
 * 하루 동선(§S-05).
 *
 * <p>연결선은 <b>직선</b>이다 — 실제 경로가 아니라 순서를 보여주는 선이다. 실제 길찾기는
 * 구글 지도 딥링크가 맡으므로 여기서 경로를 그릴 이유가 없다.
 */
export function TripDayMap({ mapped, selectedId, onSelect }: TripDayMapProps) {
  const positions = mapped.map((m) => [m.lat, m.lng] as [number, number]);

  return (
    <MapContainer
      center={positions[0]}
      zoom={14}
      scrollWheelZoom
      className="aspect-[8/5] w-full overflow-hidden rounded-lg border"
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <FitBounds points={positions} />
      {positions.length > 1 && (
        <Polyline
          positions={positions}
          pathOptions={{ color: "var(--primary)", weight: 3 }}
        />
      )}
      {mapped.map((m) => (
        <Marker
          key={m.activity.id}
          position={[m.lat, m.lng]}
          icon={numberIcon(m.order, m.activity.id === selectedId)}
          alt={`${m.order}. ${m.activity.title}`}
          eventHandlers={{ click: () => onSelect(m.activity.id) }}
        />
      ))}
    </MapContainer>
  );
}
