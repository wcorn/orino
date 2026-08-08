import "leaflet/dist/leaflet.css";

import L from "leaflet";
import { MapContainer, Marker, TileLayer } from "react-leaflet";

const pin = L.divIcon({
  className: "travel-place-pin",
  html:
    `<div style="width:14px;height:14px;border-radius:9999px;` +
    `background:var(--primary);border:2px solid var(--card);` +
    `box-shadow:0 1px 3px rgba(0,0,0,.4)"></div>`,
  iconSize: [14, 14],
  iconAnchor: [7, 7],
});

/**
 * 장소 하나짜리 미리보기(§S-07). 조작하지 않는다 — 드래그·줌을 열어두면 폼 안에서
 * 스크롤을 잡아먹는다. "여기가 어디쯤인지"만 보여주면 된다.
 */
export function PlacePreviewMap({ lat, lng }: { lat: number; lng: number }) {
  return (
    <MapContainer
      center={[lat, lng]}
      zoom={15}
      dragging={false}
      scrollWheelZoom={false}
      doubleClickZoom={false}
      zoomControl={false}
      attributionControl
      className="h-[120px] w-full overflow-hidden rounded-lg"
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <Marker position={[lat, lng]} icon={pin} />
    </MapContainer>
  );
}
