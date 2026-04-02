package ds.project.orino.common.response;

public class ApiResponse<T> {

    private final String code;
    private final T data;

    private ApiResponse(String code, T data) {
        this.code = code;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(CustomResponseCode.SUCCESS.getMessage(), data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(CustomResponseCode.SUCCESS.getMessage(), null);
    }

    public String getCode() {
        return code;
    }

    public T getData() {
        return data;
    }
}
