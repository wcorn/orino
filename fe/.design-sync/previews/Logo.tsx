import { Logo } from "orino-fe";

export function Variants() {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <Logo />
      <Logo animated />
      <Logo tone="mono" />
      <div
        style={{
          background: "#0B0B0C",
          padding: 16,
          borderRadius: 8,
          width: "fit-content",
        }}
      >
        <Logo tone="inverse" />
      </div>
      <Logo showWordmark={false} />
    </div>
  );
}
