import { Select } from "orino-fe";

const DAYS = [
  { value: "mon", label: "월요일" },
  { value: "tue", label: "화요일" },
  { value: "wed", label: "수요일" },
];

export function Default() {
  return (
    <div style={{ width: 200 }}>
      <Select
        value="wed"
        onValueChange={() => {}}
        options={DAYS}
        ariaLabelledby="day-select"
      />
    </div>
  );
}
