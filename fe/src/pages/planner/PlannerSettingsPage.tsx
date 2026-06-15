import { GoogleConnectionCard } from "@/features/google/components/GoogleConnectionCard";

export function PlannerSettingsPage() {
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-xl font-semibold">연동 설정</h1>
      <div className="max-w-md">
        <GoogleConnectionCard />
      </div>
    </div>
  );
}
