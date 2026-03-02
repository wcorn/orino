package ds.project.orino.common.response.api;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum CustomResponseCode {
    SUCCESS("요청에 성공하였습니다.");

    private final String message;

    CustomResponseCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
