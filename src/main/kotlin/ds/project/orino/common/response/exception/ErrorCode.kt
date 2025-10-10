package ds.project.orino.common.response.exception

enum class ErrorCode(val code: String, val message: String) {

    // GLOBAL
    BAD_REQUEST("GLB-ERR-001", "잘못된 요청입니다."),
    METHOD_NOT_ALLOWED("GLB-ERR-002", "허용되지 않은 메서드입니다."),
    INTERNAL_SERVER_ERROR("GLB-ERR-003", "내부 서버 오류입니다.");

    fun getMessage(e: Throwable): String {
        return "$message - ${e.message}"
    }

    fun getMessage(additionalMessage: String?): String {
        return additionalMessage?.takeIf { it.isNotBlank() }?.let {
            "$message - $it"
        } ?: message
    }
}