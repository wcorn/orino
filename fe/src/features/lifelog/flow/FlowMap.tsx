import "leaflet/dist/leaflet.css";

import L from "leaflet";
import { useEffect } from "react";
import {
  MapContainer,
  Marker,
  Polyline,
  Popup,
  TileLayer,
  useMap,
} from "react-leaflet";

import type { MomentCard } from "../api/types";
import { formatMomentTime } from "../lib/datetime";
import { toGeoMoments } from "../lib/flowGeo";

function numberIcon(order: number) {
  return L.divIcon({
    className: "lifelog-flow-pin",
    html:
      `<div style="display:flex;align-items:center;justify-content:center;` +
      `width:24px;height:24px;border-radius:9999px;background:#8b00ff;color:#fff;` +
      `font-size:12px;font-weight:600;border:2px solid #fff;box-shadow:0 1px 3px rgba(0,0,0,.4)">` +
      `${order}</div>`,
    iconSize: [24, 24],
    iconAnchor: [12, 12],
  });
}

/** 핀 좌표에 맞춰 지도 범위를 자동으로 맞춘다. */
function FitBounds({ points }: { points: [number, number][] }) {
  const map = useMap();
  useEffect(() => {
    if (points.length === 1) {
      map.setView(points[0], 14);
    } else if (points.length > 1) {
      map.fitBounds(points, { padding: [40, 40] });
    }
  }, [points, map]);
  return null;
}

/** 흐름 지도 뷰 — 좌표 있는 기록을 시간순 번호 핀 + 경로(polyline)로. (브라우저 전용) */
export function FlowMap({ moments }: { moments: MomentCard[] }) {
  const geo = toGeoMoments(moments);

  if (geo.length === 0) {
    return (
      <p className="text-muted-foreground py-12 text-center text-sm">
        위치 정보가 있는 기록이 없어요.
      </p>
    );
  }

  const positions = geo.map((g) => [g.lat, g.lng] as [number, number]);

  return (
    <MapContainer
      center={positions[0]}
      zoom={13}
      scrollWheelZoom
      style={{ height: 400, width: "100%", borderRadius: 8 }}
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <FitBounds points={positions} />
      {positions.length > 1 && (
        <Polyline
          positions={positions}
          pathOptions={{ color: "#8b00ff", weight: 3 }}
        />
      )}
      {geo.map((g) => (
        <Marker
          key={g.moment.id}
          position={[g.lat, g.lng]}
          icon={numberIcon(g.order)}
        >
          <Popup>
            <div className="flex flex-col gap-1" style={{ maxWidth: 180 }}>
              {g.moment.photos[0] && (
                <img
                  src={g.moment.photos[0].thumbUrl ?? g.moment.photos[0].url}
                  alt=""
                  style={{ width: "100%", borderRadius: 4 }}
                />
              )}
              <span style={{ fontSize: 11, color: "#666" }}>
                {formatMomentTime(g.moment.occurredAt)}
              </span>
              {g.moment.body && (
                <span style={{ fontSize: 13 }}>{g.moment.body}</span>
              )}
            </div>
          </Popup>
        </Marker>
      ))}
    </MapContainer>
  );
}
