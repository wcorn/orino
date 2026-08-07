package ds.project.orino.planner.travel.board.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.travel.board.dto.BoardResponse;
import ds.project.orino.planner.travel.board.service.BoardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/travel")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    /**
     * 보드 단일 조회. 날짜 탭·보관함 건수·선택된 날짜의 일정을 한 번에 준다.
     *
     * @param date    볼 날짜. 생략하면 진행 중인 여행은 여행 타임존의 오늘, 아니면 1일차
     * @param archive true면 날짜 대신 미배정 보관함을 본다
     */
    @GetMapping("/trips/{tripId}/board")
    public ApiResponse<BoardResponse> board(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long tripId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam(required = false, defaultValue = "false") boolean archive) {
        return ApiResponse.success(boardService.board(memberId, tripId, date, archive));
    }
}
