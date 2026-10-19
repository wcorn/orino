import { Checkbox } from "@/components/ui/checkbox";
import { FormField } from "@/components/ui/form-field";
import { TimeInput } from "@/components/ui/time-input";

interface TimeRangeFieldsProps {
  startTime: string;
  endTime: string;
  onStartChange: (value: string) => void;
  onEndChange: (value: string) => void;
  /** 종료 ≤ 시작이면 true → 종료에 인라인 에러 표시. */
  reversed: boolean;
  /** input id 접두사(예: "create" → create-start/create-end). */
  idPrefix: string;
}

/**
 * 시작/종료 시간 입력 + "자정(24:00)으로" 체크박스. 생성·편집 폼 공용.
 * 자정 체크 시 종료=24:00, 시간 입력은 비활성(자정은 native time input으로 못 고름).
 */
export function TimeRangeFields({
  startTime,
  endTime,
  onStartChange,
  onEndChange,
  reversed,
  idPrefix,
}: TimeRangeFieldsProps) {
  const midnight = endTime === "24:00";
  return (
    <>
      <div className="flex gap-3">
        <FormField
          label="시작"
          htmlFor={`${idPrefix}-start`}
          className="flex-1"
        >
          <TimeInput
            id={`${idPrefix}-start`}
            value={startTime}
            onChange={(value) => onStartChange(value)}
          />
        </FormField>
        <FormField
          label="종료"
          htmlFor={`${idPrefix}-end`}
          className="flex-1"
          error={reversed ? "종료가 시작보다 늦어야 합니다." : undefined}
        >
          <TimeInput
            id={`${idPrefix}-end`}
            value={midnight ? "" : endTime}
            disabled={midnight}
            onChange={(value) => onEndChange(value)}
          />
        </FormField>
      </div>

      <label className="flex items-center gap-1.5 text-xs">
        <Checkbox
          checked={midnight}
          onChange={(e) => onEndChange(e.target.checked ? "24:00" : "23:00")}
        />
        종료를 자정(24:00)으로
      </label>
    </>
  );
}
