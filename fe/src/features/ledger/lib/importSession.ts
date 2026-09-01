import type {
  ImportAnalyzeResponse,
  ImportFileMapping,
  ImportMapping,
} from "@/features/ledger/api/ledger";

/** 매핑할 수 있는 자리. 날짜와 금액만 필수고 나머지는 비워 둘 수 있다. */
export const FIELDS = [
  { key: "date", label: "날짜", required: true },
  { key: "amount", label: "금액", required: false },
  { key: "inflow", label: "입금", required: false },
  { key: "outflow", label: "출금", required: false },
  { key: "title", label: "내용", required: false },
  { key: "memo", label: "메모", required: false },
  { key: "type", label: "유형", required: false },
  { key: "category", label: "카테고리", required: false },
  { key: "asset", label: "자산", required: false },
] as const;

export type FieldKey = (typeof FIELDS)[number]["key"];

export type MappingDraft = Record<FieldKey, number | null>;

export const EMPTY_MAPPING: MappingDraft = {
  date: null,
  amount: null,
  inflow: null,
  outflow: null,
  title: null,
  memo: null,
  type: null,
  category: null,
  asset: null,
};

/**
 * 고른 파일 한 장과 그 파일에 대한 결정 전부.
 *
 * **파일마다 따로 든다.** 은행 내역과 카드 명세서를 함께 올릴 수 있어야 하고, 그 둘은
 * 열 구성도 들어갈 자산도 다르다 — 하나로 묶으면 섞어 올리는 길이 막힌다.
 */
export type ImportFileState = {
  /**
   * 목록에서 이 줄을 가려내는 열쇠.
   *
   * 이름만으로는 모자란다 — 같은 이름의 파일을 두 번 고를 수 있고, 그때 한 줄을 빼면
   * 엉뚱한 줄이 사라진다.
   */
  key: string;
  file: File;
  /** 이 파일의 비밀번호. 화면을 벗어나면 사라지고 서버도 그 요청에서만 쓴다. */
  password: string;
  /** 읽어 본 결과. `null`이면 아직 못 읽었다(읽는 중이거나 실패). */
  analysis: ImportAnalyzeResponse | null;
  /** 못 읽은 이유. 파일마다 다르다 — 한 장만 암호가 걸려 있을 수 있다. */
  failure: string | null;
  busy: boolean;
  assetId: number | null;
  skipRows: number;
  mapping: MappingDraft;
  /** 이 파일이 만들 배치의 이름. 기본값은 파일 이름이다. */
  source: string;
};

let sequence = 0;

export function newFileState(file: File): ImportFileState {
  sequence += 1;
  return {
    key: `${file.name}:${file.size}:${file.lastModified}:${sequence}`,
    file,
    password: "",
    analysis: null,
    failure: null,
    busy: true,
    assetId: null,
    skipRows: 1,
    mapping: { ...EMPTY_MAPPING },
    source: file.name.replace(/\.[^.]+$/, ""),
  };
}

function errorCode(error: unknown): string | undefined {
  return (error as { response?: { data?: { code?: string } } } | undefined)
    ?.response?.data?.code;
}

/**
 * 실패를 사람 말로.
 *
 * 「읽을 수 없어요, CSV 또는 .xlsx만 됩니다」로 뭉뚱그리면 **.xlsx를 들고 있는 사람**은
 * 무엇을 고쳐야 할지 모른다 — 은행 파일은 암호가 걸린 채로 내려오고, 그게 이 화면에서
 * 가장 흔한 실패다(#1318).
 */
export function readFailure(error: unknown): string {
  const code = errorCode(error);
  if (code === "LDG-ERR-035") {
    return "암호가 걸린 파일이에요. 비밀번호를 적고 다시 읽어 주세요.";
  }
  if (code === "LDG-ERR-036") {
    return "비밀번호가 맞지 않아요. 다시 확인해 주세요.";
  }
  if (code === "LDG-ERR-025") {
    return "한 번에 넣을 수 있는 줄 수를 넘었어요. 기간을 나눠 내려받아 주세요.";
  }
  return "이 파일은 읽을 수 없어요. CSV 또는 .xlsx만 됩니다.";
}

/** 여러 장을 한 번에 보낼 때의 실패. 어느 파일 때문인지는 서버가 알려주지 않는다. */
export function sendFailure(error: unknown, fallback: string): string {
  const code = errorCode(error);
  if (code === "LDG-ERR-037") {
    return "파일이 너무 많아요. 스무 장까지 한 번에 넣을 수 있습니다.";
  }
  if (code === "LDG-ERR-025") {
    return "모든 파일을 합친 줄 수가 한도를 넘었어요. 나눠서 넣어 주세요.";
  }
  if (code === "LDG-ERR-035" || code === "LDG-ERR-036") {
    return readFailure(error);
  }
  return fallback;
}

/** 날짜와 금액, 그리고 들어갈 자산. 셋이 있어야 이 파일을 읽어 볼 수 있다. */
export function isMappingReady(state: ImportFileState): boolean {
  const { mapping } = state;
  const hasAmount =
    mapping.amount !== null ||
    mapping.inflow !== null ||
    mapping.outflow !== null;
  return mapping.date !== null && hasAmount && state.assetId !== null;
}

/**
 * 열 구성이 같은 파일인가.
 *
 * 설정을 퍼뜨릴 대상을 고르는 잣대다. 열이 다른 파일에 같은 매핑을 씌우면 한 칸씩 밀린
 * 줄이 「오류」가 아니라 **그럴듯하게 틀린 줄**로 들어간다.
 */
export function sameHeaders(a: ImportFileState, b: ImportFileState): boolean {
  const left = a.analysis?.headers;
  const right = b.analysis?.headers;
  if (!left || !right || left.length !== right.length) {
    return false;
  }
  return left.every((header, index) => header === right[index]);
}

export function toFileMapping(state: ImportFileState): ImportFileMapping {
  return {
    assetId: state.assetId as number,
    skipRows: state.skipRows,
    mapping: state.mapping as ImportMapping,
    password: state.password || undefined,
  };
}

/**
 * 넣을 줄을 가리키는 열쇠.
 *
 * 줄 번호는 **파일 안에서** 센다 — 파일이 여러 장이면 3번 줄이 여러 개라, 번호만으로는
 * 어느 줄을 켰는지 말할 수 없다.
 */
export function rowKey(fileIndex: number, rowNumber: number): string {
  return `${fileIndex}:${rowNumber}`;
}
