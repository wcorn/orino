import { BookOpen, CheckSquare, Plus } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { AddMaterialDialog } from "@/features/material/components/AddMaterialDialog";
import { useMaterials } from "@/features/material/hooks/useMaterials";
import { useTodayReviews } from "@/features/review/hooks/useTodayReviews";

function TodayReviewCard() {
  const { data, isLoading } = useTodayReviews();
  const reviews = data?.reviews ?? [];
  const total = reviews.length;
  const overdue = reviews.filter((r) => r.delayDays > 0).length;

  return (
    <Card>
      <CardContent>
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-2">
            <CheckSquare className="text-primary size-4" />
            <h2 className="text-base font-medium">오늘 복습</h2>
          </div>

          {isLoading ? (
            <p className="text-muted-foreground text-sm">불러오는 중...</p>
          ) : total === 0 ? (
            <p className="text-muted-foreground text-sm">
              0건 — 모두 완료했어요!
            </p>
          ) : (
            <div className="flex flex-col gap-1">
              <p className="text-foreground text-2xl font-semibold">
                {total}건
              </p>
              {overdue > 0 && (
                <p className="text-destructive text-xs">
                  밀린 복습 {overdue}건 포함
                </p>
              )}
            </div>
          )}

          <Link
            to="/planner/reviews/today"
            className="text-primary w-fit text-sm font-medium hover:underline"
          >
            바로가기 →
          </Link>
        </div>
      </CardContent>
    </Card>
  );
}

function MaterialsCard() {
  const { data, isLoading } = useMaterials("ACTIVE");
  const total = data?.length ?? 0;
  const [dialogOpen, setDialogOpen] = useState(false);

  return (
    <Card>
      <CardContent>
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-2">
            <BookOpen className="text-primary size-4" />
            <h2 className="text-base font-medium">학습 자료</h2>
          </div>

          {isLoading ? (
            <p className="text-muted-foreground text-sm">불러오는 중...</p>
          ) : total === 0 ? (
            <div className="flex flex-col gap-2">
              <p className="text-muted-foreground text-sm">
                아직 등록된 자료가 없어요.
              </p>
              <Button
                variant="outline"
                size="sm"
                className="w-fit"
                onClick={() => setDialogOpen(true)}
              >
                <Plus className="size-3.5" /> 첫 자료 추가
              </Button>
            </div>
          ) : (
            <p className="text-foreground text-2xl font-semibold">
              {total}개 진행 중
            </p>
          )}

          {total > 0 && (
            <Link
              to="/planner/materials"
              className="text-primary w-fit text-sm font-medium hover:underline"
            >
              바로가기 →
            </Link>
          )}
        </div>
      </CardContent>
      <AddMaterialDialog open={dialogOpen} onOpenChange={setDialogOpen} />
    </Card>
  );
}

export function HomePage() {
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-xl font-semibold">안녕하세요 👋</h1>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TodayReviewCard />
        <MaterialsCard />
      </div>
    </div>
  );
}
