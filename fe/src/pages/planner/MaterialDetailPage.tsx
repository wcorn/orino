import { Tabs } from "@base-ui/react/tabs";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";

import { MaterialHeader } from "@/features/material/components/MaterialHeader";
import { useMaterial } from "@/features/material/hooks/useMaterial";
import { NoteEditor } from "@/features/note/components/NoteEditor";
import { useNote } from "@/features/note/hooks/useNote";

type TabValue = "note" | "cards";

const VALID_TABS: TabValue[] = ["note", "cards"];

export function MaterialDetailPage() {
  const { id } = useParams<{ id: string }>();
  const materialId = Number(id);
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const tabParam = searchParams.get("tab");
  const tab: TabValue = VALID_TABS.includes(tabParam as TabValue)
    ? (tabParam as TabValue)
    : "note";

  const handleTabChange = (next: TabValue) => {
    navigate(`/planner/materials/${materialId}?tab=${next}`, { replace: true });
  };

  const materialQuery = useMaterial(materialId);
  const noteQuery = useNote(materialId);

  if (materialQuery.isLoading || noteQuery.isLoading) {
    return <p className="text-muted-foreground text-sm">불러오는 중...</p>;
  }
  if (materialQuery.isError || !materialQuery.data) {
    return (
      <p className="text-destructive text-sm">자료를 불러오지 못했어요.</p>
    );
  }
  if (noteQuery.isError || !noteQuery.data) {
    return (
      <p className="text-destructive text-sm">노트를 불러오지 못했어요.</p>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <MaterialHeader material={materialQuery.data} />

      <Tabs.Root
        value={tab}
        onValueChange={(v) => handleTabChange(v as TabValue)}
      >
        <Tabs.List className="border-border flex gap-1 border-b">
          <Tabs.Tab
            value="note"
            className="text-muted-foreground data-[selected]:text-foreground data-[selected]:border-foreground -mb-px cursor-pointer rounded-t-md border-b-2 border-transparent px-3 py-2 text-sm font-medium"
          >
            📝 노트
          </Tabs.Tab>
          <Tabs.Tab
            value="cards"
            className="text-muted-foreground data-[selected]:text-foreground data-[selected]:border-foreground -mb-px cursor-pointer rounded-t-md border-b-2 border-transparent px-3 py-2 text-sm font-medium"
          >
            📇 카드 {materialQuery.data.flashcardCount}
          </Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="note" className="pt-4">
          <NoteEditor
            materialId={materialId}
            initialContent={noteQuery.data.content}
          />
        </Tabs.Panel>
        <Tabs.Panel value="cards" className="pt-4">
          <p className="text-muted-foreground text-sm">
            카드 탭은 곧 만나요. (#373)
          </p>
        </Tabs.Panel>
      </Tabs.Root>
    </div>
  );
}
