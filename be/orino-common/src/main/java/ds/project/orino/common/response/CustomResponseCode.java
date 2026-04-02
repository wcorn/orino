package ds.project.orino.common.response;

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
