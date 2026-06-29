import { PageHeader } from "@/components/PageHeader";

export function HomePage() {
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="안녕하세요 👋" />
      <p className="text-muted-foreground text-sm">
        Study Planner v2 준비 중이에요. 곧 새 기능으로 만나요.
      </p>
    </div>
  );
}
