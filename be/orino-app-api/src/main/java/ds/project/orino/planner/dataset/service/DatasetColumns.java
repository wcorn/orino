package ds.project.orino.planner.dataset.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.dataset.dto.DatasetColumn;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** {@code dataset.columns_json} 변환. 열 순서가 곧 표시 순서이자 투영 순서다. */
final class DatasetColumns {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<DatasetColumn>> TYPE = new TypeReference<>() {
    };

    private DatasetColumns() {
    }

    static List<DatasetColumn> parse(String json) {
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }
}
