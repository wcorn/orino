import { Switch } from "orino-fe";

export function On() {
  return <Switch checked aria-label="알림 켜짐" onCheckedChange={() => {}} />;
}

export function Off() {
  return (
    <Switch checked={false} aria-label="알림 꺼짐" onCheckedChange={() => {}} />
  );
}
