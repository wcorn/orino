import { Wordmark } from "orino-fe";

export function Sizes() {
  return (
    <div style={{ display: "flex", alignItems: "baseline", gap: 20 }}>
      <Wordmark size={20} />
      <Wordmark size={28} />
      <Wordmark size={40} />
      <div style={{ background: "#0B0B0C", padding: 12, borderRadius: 8 }}>
        <Wordmark size={28} tone="inverse" />
      </div>
    </div>
  );
}
