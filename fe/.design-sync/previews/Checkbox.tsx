import { Checkbox } from "orino-fe";

export function States() {
  return (
    <div style={{ display: "flex", gap: 16, alignItems: "center" }}>
      <label style={{ display: "flex", gap: 6, alignItems: "center" }}>
        <Checkbox defaultChecked />
        선택됨
      </label>
      <label style={{ display: "flex", gap: 6, alignItems: "center" }}>
        <Checkbox />
        선택 안 함
      </label>
      <label
        style={{
          display: "flex",
          gap: 6,
          alignItems: "center",
          opacity: 0.5,
        }}
      >
        <Checkbox disabled />
        비활성
      </label>
    </div>
  );
}
