import { Link, useParams } from "react-router-dom";

export function MaterialDetailPage() {
  const { id } = useParams<{ id: string }>();

  return (
    <div className="flex flex-col gap-4">
      <Link
        to="/planner/materials"
        className="text-muted-foreground hover:text-foreground w-fit text-sm"
      >
        ← 뒤로
      </Link>
      <h1 className="text-xl font-semibold">학습 자료 #{id}</h1>
      <p className="text-muted-foreground text-sm">
        곧 단위 목록이 표시됩니다.
      </p>
    </div>
  );
}
