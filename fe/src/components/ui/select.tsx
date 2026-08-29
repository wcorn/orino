import { Select as SelectPrimitive } from "@base-ui/react/select";
import { Check, ChevronDown } from "lucide-react";
import type { ReactNode } from "react";

export interface SelectOption<T extends string> {
  value: T;
  label: ReactNode;
}

interface SelectProps<T extends string> {
  value: T;
  onValueChange: (value: T) => void;
  options: SelectOption<T>[];
  ariaLabelledby?: string;
  /** 보이는 라벨이 없을 때(표 안의 셀 등) 대신 읽을 이름. */
  ariaLabel?: string;
  /** 비활성. 켤 수 없는 설정을 감추지 않고 회색으로 남겨 무엇이 있는지 알린다. */
  disabled?: boolean;
}

/** 표준 드롭다운 셀렉트. Trigger·Popup·Item 스타일을 일원화한다. */
function Select<T extends string>({
  value,
  onValueChange,
  options,
  ariaLabelledby,
  ariaLabel,
  disabled = false,
}: SelectProps<T>) {
  const labelOf = (v: T) => options.find((o) => o.value === v)?.label;
  return (
    <SelectPrimitive.Root
      value={value}
      onValueChange={(v) => onValueChange(v as T)}
      disabled={disabled}
    >
      <SelectPrimitive.Trigger
        aria-labelledby={ariaLabelledby}
        aria-label={ariaLabel}
        className="border-input bg-background focus-visible:ring-ring/30 flex h-9 items-center justify-between gap-2 rounded-md border px-3 text-sm shadow-xs focus-visible:ring-2 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50"
      >
        <SelectPrimitive.Value>
          {(v) => <span>{labelOf(v as T)}</span>}
        </SelectPrimitive.Value>
        <SelectPrimitive.Icon>
          <ChevronDown className="size-4 opacity-60" />
        </SelectPrimitive.Icon>
      </SelectPrimitive.Trigger>
      <SelectPrimitive.Portal>
        <SelectPrimitive.Positioner sideOffset={4} className="z-50">
          <SelectPrimitive.Popup className="bg-popover text-popover-foreground min-w-(--anchor-width) overflow-hidden rounded-md border p-1 shadow-md">
            {options.map((o) => (
              <SelectPrimitive.Item
                key={o.value}
                value={o.value}
                className="data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground flex cursor-pointer items-center justify-between gap-2 rounded-sm px-2 py-1.5 text-sm outline-none"
              >
                <SelectPrimitive.ItemText>
                  <span>{o.label}</span>
                </SelectPrimitive.ItemText>
                <SelectPrimitive.ItemIndicator>
                  <Check className="size-3.5" />
                </SelectPrimitive.ItemIndicator>
              </SelectPrimitive.Item>
            ))}
          </SelectPrimitive.Popup>
        </SelectPrimitive.Positioner>
      </SelectPrimitive.Portal>
    </SelectPrimitive.Root>
  );
}

export { Select };
