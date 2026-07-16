/**
 * 레거시 native 표(`table` 노드) → dataset 그리드 일괄 마이그레이션 (일회성).
 *
 * 노트 본문을 순회하며 TableKit `table` 노드를 dataset(+dataset_row)으로 옮기고
 * `datasetTable` 참조 노드로 치환한다. 변환 규칙(컬럼 생성)은 앱의 표 삽입·Import와
 * 동일한 순수 함수(datasetColumns·tableMigration)를 그대로 재사용한다(SSOT).
 *
 * 사용법:
 *   # dry-run(기본) — 변환 대상·손실 경고만 리포트, 서버 변경 없음
 *   ORINO_TOKEN=<accessToken> npx tsx scripts/migrate-tables-to-datasets.ts
 *
 *   # 실제 적용
 *   ORINO_TOKEN=<accessToken> DRY_RUN=false npx tsx scripts/migrate-tables-to-datasets.ts
 *
 * 환경변수:
 *   ORINO_TOKEN     (필수) 브라우저 devtools 요청의 Authorization 헤더 Bearer 값
 *   ORINO_API_BASE  (기본 https://api.orino.dev/api)
 *   ORINO_TIMEZONE  (기본 Asia/Seoul) 서버 X-Timezone 헤더
 *   DRY_RUN         (기본 true) "false"일 때만 실제 dataset 생성·노트 저장
 */
import type { JSONContent } from "@tiptap/react";
import axios from "axios";

import { buildDatasetColumns } from "../src/features/note/import/datasetColumns";
import type { NormalizedTable } from "../src/features/note/import/tableContent";
import { migrateDocTables } from "../src/features/note/import/tableMigration";

const API_BASE = process.env.ORINO_API_BASE ?? "https://api.orino.dev/api";
const TOKEN = process.env.ORINO_TOKEN;
const TIMEZONE = process.env.ORINO_TIMEZONE ?? "Asia/Seoul";
const DRY_RUN = process.env.DRY_RUN !== "false";
/** BE 벌크 한 요청당 행 상한(2000)보다 작게. */
const BULK_CHUNK = 1000;

if (!TOKEN) {
  console.error("ORINO_TOKEN 환경변수가 필요합니다.");
  process.exit(1);
}

const api = axios.create({
  baseURL: API_BASE,
  headers: { Authorization: `Bearer ${TOKEN}`, "X-Timezone": TIMEZONE },
});

interface Envelope<T> {
  code: string;
  data: T;
}
interface TreeNode {
  id: number;
  children: TreeNode[];
}

function flattenIds(nodes: TreeNode[]): number[] {
  const ids: number[] = [];
  const walk = (n: TreeNode) => {
    ids.push(n.id);
    (n.children ?? []).forEach(walk);
  };
  nodes.forEach(walk);
  return ids;
}

/** 독립 노트 + 모든 학습자료 노트를 합쳐 전체 노트 id를 모은다(중복 제거). */
async function allNoteIds(): Promise<number[]> {
  const ids = new Set<number>();

  const independent = await api.get<Envelope<{ notes: TreeNode[] }>>("/notes");
  flattenIds(independent.data.data.notes).forEach((id) => ids.add(id));

  const materials =
    await api.get<Envelope<{ materials: { id: number }[] }>>(
      "/planner/materials",
    );
  for (const material of materials.data.data.materials) {
    const tree = await api.get<Envelope<{ notes: TreeNode[] }>>("/notes", {
      params: { materialId: material.id },
    });
    flattenIds(tree.data.data.notes).forEach((id) => ids.add(id));
  }

  return [...ids];
}

/** apply 모드 convert: dataset 생성 + 행 벌크 업로드 후 datasetId 반환. */
async function createDatasetFromTable(table: NormalizedTable): Promise<number> {
  const created = await api.post<Envelope<{ id: number }>>("/datasets", {
    columns: buildDatasetColumns(table),
  });
  const datasetId = created.data.data.id;
  for (let i = 0; i < table.rows.length; i += BULK_CHUNK) {
    await api.post(`/datasets/${datasetId}/rows/bulk`, {
      rows: table.rows.slice(i, i + BULK_CHUNK),
    });
  }
  return datasetId;
}

async function main(): Promise<void> {
  console.log(`[migrate] API=${API_BASE} DRY_RUN=${DRY_RUN}`);
  const noteIds = await allNoteIds();
  console.log(`[migrate] 노트 ${noteIds.length}개 스캔`);

  let totalTables = 0;
  let changedNotes = 0;
  const warnCount: Record<string, number> = {};
  // dry-run은 서버를 건드리지 않는 로컬 스텁(id 0)을 쓴다.
  const convert = DRY_RUN
    ? async () => 0
    : (table: NormalizedTable) => createDatasetFromTable(table);

  for (const noteId of noteIds) {
    const detail = await api.get<
      Envelope<{ content: JSONContent; title: string }>
    >(`/notes/${noteId}`);
    const { content, title } = detail.data.data;
    const { doc, converted, warnings } = await migrateDocTables(
      content,
      convert,
    );
    if (converted === 0) continue;

    totalTables += converted;
    changedNotes += 1;
    for (const w of warnings) warnCount[w.kind] = (warnCount[w.kind] ?? 0) + 1;
    console.log(
      `  note#${noteId} "${title}" — 표 ${converted}개` +
        (warnings.length ? `, 손실 경고 ${warnings.length}` : ""),
    );

    if (!DRY_RUN) await api.patch(`/notes/${noteId}`, { content: doc });
  }

  console.log(
    `\n[migrate] ${DRY_RUN ? "(dry-run) " : ""}표 ${totalTables}개 / 노트 ${changedNotes}개 ` +
      (DRY_RUN ? "변환 예정" : "변환 완료"),
  );
  if (Object.keys(warnCount).length) {
    console.log(
      "[migrate] 손실 경고:",
      Object.entries(warnCount)
        .map(([kind, n]) => `${kind}=${n}`)
        .join(", "),
    );
  }
  if (DRY_RUN) console.log("[migrate] 실제 적용하려면 DRY_RUN=false 로 재실행");
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : String(error);
  console.error("[migrate] 실패:", message);
  process.exit(1);
});
