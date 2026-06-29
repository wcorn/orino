import { PageHeader } from "@/components/PageHeader";
import { GoogleConnectionCard } from "@/features/google/components/GoogleConnectionCard";

export function PlannerSettingsPage() {
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="연동 설정" />
      <div className="max-w-md">
        <GoogleConnectionCard />
      </div>
    </div>
  );
}
