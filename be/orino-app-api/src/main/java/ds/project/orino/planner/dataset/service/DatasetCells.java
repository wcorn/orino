package ds.project.orino.planner.dataset.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.dataset.dto.DatasetColumn;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code dataset_row.cells}(열 key → 값 맵)와 API의 위치 배열 사이를 오간다.
 * 저장은 주소로, API는 위치로 — 그 경계가 여기다.
 */
final class DatasetCells {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {
    };

    private DatasetCells() {
    }

    static Map<String, String> parse(String json) {
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    static String serialize(Map<String, String> cells) {
        try {
            return MAPPER.writeValueAsString(cells);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, e);
        }
    }

    /** 저장소 맵 → API 위치 배열. 열 순서대로 뽑고, 값이 없는 열은 빈 문자열로 채운다. */
    static List<String> toList(String cellsJson, List<DatasetColumn> columns) {
        Map<String, String> cells = parse(cellsJson);
        List<String> list = new ArrayList<>(columns.size());
        for (DatasetColumn column : columns) {
            list.add(cells.getOrDefault(column.key(), ""));
        }
        return list;
    }

    /**
     * API 위치 배열 → 저장소 맵. 열 순서로 짝지으며, 열 수를 넘는 값은 담을 key가 없어 버린다.
     * 열 수보다 짧으면 나머지 열은 빈 문자열로 채워 직사각형을 유지한다.
     */
    static Map<String, String> toMap(List<String> cells, List<DatasetColumn> columns) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            String value = i < cells.size() ? cells.get(i) : null;
            map.put(columns.get(i).key(), value == null ? "" : value);
        }
        return map;
    }
}
