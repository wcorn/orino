package ds.project.orino.common.response.exception


class CustomException : RuntimeException {
    val errorCode: ErrorCode

    constructor(errorCode: ErrorCode) : super(errorCode.message) {
        this.errorCode = errorCode
    }

    constructor(errorCode: ErrorCode, cause: Throwable) :
            super(errorCode.getMessage(cause), cause) {
        this.errorCode = errorCode
    }
}