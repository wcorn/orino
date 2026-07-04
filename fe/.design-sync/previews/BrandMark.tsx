import { BrandMark } from "orino-fe";

export function Sizes() {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
      <BrandMark size={16} />
      <BrandMark size={24} />
      <BrandMark size={40} />
      <BrandMark size={40} tone="mono" />
      <div style={{ background: "#0B0B0C", padding: 12, borderRadius: 8 }}>
        <BrandMark size={40} tone="inverse" />
      </div>
    </div>
  );
}
