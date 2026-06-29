import { Tooltip, TooltipContent, TooltipTrigger } from "orino-fe";

export function Default() {
  return (
    <div style={{ padding: 48 }}>
      <Tooltip defaultOpen>
        <TooltipTrigger>도움말</TooltipTrigger>
        <TooltipContent>마감 기준 시각입니다</TooltipContent>
      </Tooltip>
    </div>
  );
}
