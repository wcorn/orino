import type { MaterialType } from "./api/materials";

export const MATERIAL_TYPE_ICONS: Record<MaterialType, string> = {
  BOOK: "📕",
  LECTURE: "🎬",
  WORKBOOK: "📘",
  MOOC: "🎓",
};

export const MATERIAL_TYPE_LABELS: Record<MaterialType, string> = {
  BOOK: "책",
  LECTURE: "강의",
  WORKBOOK: "문제집",
  MOOC: "MOOC",
};
