package ds.project.orino.common.response.api

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonFormat.Shape.OBJECT

@JsonFormat(shape = OBJECT)
enum class CustomResponseCode(val message: String) {
    SUCCESS("요청에 성공하였습니다.")
}