import { SectionHeader } from "orino-fe";

export function Sizes() {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      <SectionHeader>오늘의 루틴</SectionHeader>
      <SectionHeader size="sm" level={3}>
        일정 (3)
      </SectionHeader>
    </div>
  );
}
