import { useState } from "react";

import type { DatasetMeta } from "./api/datasets";

interface Props {
  /** 참조 가능한 표들(이름 있는 형제 표만). */
  tables: Array<DatasetMeta & { name: string }>;
  /** 고른 참조 토큰({표!열}행)을 삽입한다. */
  onInsert: (token: string) => void;
  onClose: () => void;
}

/**
 * 표간 참조 삽입 피커(R9 #918). 다른 표·열·행을 골라 {@code {표이름!열}행} 토큰을 만든다.
 * 저장 시 BE가 표 이름을 datasetId로 풀어 대상 표 셀을 읽는다.
 *
 * <p>{@code data-cross-picker}로 감싸, 셀 입력창의 blur가 이 안으로 향할 땐 편집을 닫지 않는다
 * (피커를 쓰는 동안 편집이 살아 있어야 삽입이 가능하다).
 */
export function CrossRefPicker({ tables, onInsert, onClose }: Props) {
  const [tableId, setTableId] = useState(tables[0].id);
  const table = tables.find((t) => t.id === tableId) ?? tables[0];
  const [colKey, setColKey] = useState(table.columns[0]?.key ?? "");
  const [row, setRow] = useState(1);

  const col = table.columns.find((c) => c.key === colKey) ?? table.columns[0];

  const insert = () => {
    if (!col) return;
    onInsert(`{${table.name}!${col.label}}${row}`);
  };

  return (
    <div
      data-cross-picker
      role="dialog"
      aria-label="표간 참조 삽입"
      className="border-border bg-popover absolute top-0 right-0 z-50 flex flex-col gap-2 rounded-md border p-2 text-sm shadow-md"
    >
      <label className="flex items-center gap-2">
        <span className="text-muted-foreground w-8 text-xs">표</span>
        <select
          aria-label="참조할 표"
          value={tableId}
          onChange={(e) => {
            const next = tables.find((t) => t.id === Number(e.target.value));
            if (!next) return;
            setTableId(next.id);
            setColKey(next.columns[0]?.key ?? "");
          }}
          className="border-border rounded border px-1 py-0.5"
        >
          {tables.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </label>
      <label className="flex items-center gap-2">
        <span className="text-muted-foreground w-8 text-xs">열</span>
        <select
          aria-label="참조할 열"
          value={colKey}
          onChange={(e) => setColKey(e.target.value)}
          className="border-border rounded border px-1 py-0.5"
        >
          {table.columns.map((c) => (
            <option key={c.key} value={c.key}>
              {c.label}
            </option>
          ))}
        </select>
      </label>
      <label className="flex items-center gap-2">
        <span className="text-muted-foreground w-8 text-xs">행</span>
        <input
          aria-label="행 번호"
          type="number"
          min={1}
          value={row}
          onChange={(e) => setRow(Math.max(1, Number(e.target.value) || 1))}
          className="border-border w-16 rounded border px-1 py-0.5"
        />
      </label>
      <div className="flex justify-end gap-1">
        <button
          type="button"
          onClick={onClose}
          className="hover:bg-accent rounded px-2 py-1 text-xs"
        >
          닫기
        </button>
        <button
          type="button"
          onClick={insert}
          className="bg-primary text-primary-foreground rounded px-2 py-1 text-xs"
        >
          삽입
        </button>
      </div>
    </div>
  );
}
