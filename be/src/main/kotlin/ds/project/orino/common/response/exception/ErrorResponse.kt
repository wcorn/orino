package ds.project.orino.common.response.exception

data class ErrorResponse(
    val code: String,
    val message: String
) {
    companion object {
        fun of(code: ErrorCode): ErrorResponse =
            ErrorResponse(code.code, code.message)

        fun of(code: ErrorCode, e: Exception): ErrorResponse =
            ErrorResponse(code.code, code.getMessage(e))

        fun of(code: ErrorCode, message: String?): ErrorResponse =
            ErrorResponse(code.code, code.getMessage(message))
    }
}
