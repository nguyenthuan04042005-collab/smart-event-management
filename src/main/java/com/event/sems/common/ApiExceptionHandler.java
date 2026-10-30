package com.event.sems.common;

import com.event.sems.dto.ApiDtos.ErrorView;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorView> api(ApiException ex) {
    return ResponseEntity.status(ex.getStatus())
        .body(new ErrorView(ex.getMessage(), LocalDateTime.now()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorView> validation(MethodArgumentNotValidException ex) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(e -> "Dữ liệu không hợp lệ ở trường " + e.getField())
            .orElse("Dữ liệu không hợp lệ");
    return ResponseEntity.badRequest().body(new ErrorView(detail, LocalDateTime.now()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorView> conflict(DataIntegrityViolationException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorView("Dữ liệu đã tồn tại hoặc vi phạm ràng buộc.", LocalDateTime.now()));
  }
}
