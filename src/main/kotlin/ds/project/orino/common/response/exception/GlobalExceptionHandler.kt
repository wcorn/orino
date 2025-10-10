package ds.project.orino.common.response.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("handleException: {}", e.message)
        val errorResponse = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    @ExceptionHandler(CustomException::class)
    fun handleCustomException(e: CustomException): ResponseEntity<ErrorResponse> {
        log.error("handleCustomException: {}", e.errorCode.toString())
        val errorResponse = ErrorResponse.of(e.errorCode)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleHttpRequestMethodNotSupportedException(e: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> {
        log.error("handleHttpRequestMethodNotSupportedException: {}", e.message)
        val errorResponse = ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED)
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun processValidationError(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        log.error("processValidationError: {}", e.message)
        val errorResponse = ErrorResponse.of(
            ErrorCode.BAD_REQUEST,
            e.bindingResult.allErrors[0].defaultMessage
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun methodArgumentTypeMismatchExceptionError(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        log.error("MethodArgumentError: {}", e.message)
        val errorResponse = ErrorResponse.of(ErrorCode.BAD_REQUEST, e)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }
}
