import { Tabs } from "@base-ui/react/tabs";
import { useParams, useSearchParams } from "react-router-dom";

import { FlashcardListTab } from "@/features/flashcard/components/FlashcardListTab";
import { MaterialHeader } from "@/features/material/components/MaterialHeader";
import { useMaterial } from "@/features/material/hooks/useMaterial";
import { NoteTab } from "@/features/note/components/NoteTab";

type TabValue = "note" | "cards";

const VALID_TABS: TabValue[] = ["note", "cards"];

export function MaterialDetailPage() {
  const { id } = useParams<{ id: string }>();
  const materialId = Number(id);
  const [searchParams, setSearchParams] = useSearchParams();

  const tabParam = searchParams.get("tab");
  const tab: TabValue = VALID_TABS.includes(tabParam as TabValue)
    ? (tabParam as TabValue)
    : "note";

  const handleTabChange = (next: TabValue) => {
    setSearchParams(
      (prev) => {
        const params = new URLSearchParams(prev);
        params.set("tab", next);
        // 카드 탭으로 가면 활성 노트 쿼리는 정리
        if (next !== "note") {
          params.delete("note");
        }
        return params;
      },
      { replace: true },
    );
  };

  const materialQuery = useMaterial(materialId);

  if (materialQuery.isLoading) {
    return <p className="text-muted-foreground text-sm">불러오는 중...</p>;
  }
  if (materialQuery.isError || !materialQuery.data) {
    return (
      <p className="text-destructive text-sm">자료를 불러오지 못했어요.</p>
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
          <NoteTab materialId={materialId} />
        </Tabs.Panel>
        <Tabs.Panel value="cards" className="pt-4">
          <FlashcardListTab materialId={materialId} />
        </Tabs.Panel>
      </Tabs.Root>
    </div>
  );
}
