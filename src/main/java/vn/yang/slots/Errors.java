package vn.yang.slots;
import java.util.Map;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class Errors {
  @ExceptionHandler(Problem.class) ResponseEntity<?> problem(Problem p){return response(p.status(),p.getMessage());}
  @ExceptionHandler({HttpMessageNotReadableException.class,MissingRequestValueException.class,MethodArgumentTypeMismatchException.class})
  ResponseEntity<?> bad(Exception e){return response(400,"JSON hoặc tham số không hợp lệ.");}
  @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(Exception e){return response(409,"Dữ liệu xung đột. Hãy tải lại trước khi thử.");}
  @ExceptionHandler(PessimisticLockingFailureException.class) ResponseEntity<?> busy(Exception e){return response(409,"Có yêu cầu khác đang cập nhật. Hãy thử lại với cùng mã yêu cầu.");}
  private ResponseEntity<?> response(int status,String error){return ResponseEntity.status(status).body(Map.of("error",error));}
}
