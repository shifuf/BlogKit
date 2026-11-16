package com.blog.system.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.blog.system.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({EditConflictException.class, org.springframework.dao.OptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleConflict(Exception exception) {
        return ApiResponse.error("文章已被其他窗口修改，请比较最新版本后重新保存");
    }

    @ExceptionHandler({cn.dev33.satoken.exception.NotRoleException.class, cn.dev33.satoken.exception.NotPermissionException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleRole(Exception exception) { return ApiResponse.error("没有操作权限"); }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleMissing(NotFoundException exception) { return ApiResponse.error(exception.getMessage()); }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        log.warn("Business exception: {}", exception.getMessage());
        return ApiResponse.error(exception.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbiddenException(ForbiddenException exception) {
        log.warn("Forbidden exception: {}", exception.getMessage());
        return ApiResponse.error(exception.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleNotLoginException(NotLoginException exception) {
        log.warn("Not login exception: {}", exception.getMessage());
        return ApiResponse.error("请先登录");
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(Exception exception) {
        log.warn("Validation exception", exception);
        return ApiResponse.error("请求参数不合法");
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            TransactionSystemException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleDataException(Exception exception) {
        String trace = java.util.UUID.randomUUID().toString();
        log.error("Data exception trace=" + trace, exception);
        return ApiResponse.error("数据写入失败，请重试；追踪编号：" + trace);
    }

    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFoundException(Exception exception) {
        return ApiResponse.error("接口不存在");
    }

    @ExceptionHandler(TotpRequiredException.class)
    @ResponseStatus(HttpStatus.PRECONDITION_REQUIRED)
    public ApiResponse<Void> handleTotpRequired(TotpRequiredException exception) {
        return ApiResponse.error(exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception exception) {
        String trace = java.util.UUID.randomUUID().toString();
        log.error("Unhandled server exception trace=" + trace, exception);
        return ApiResponse.error("服务器内部错误；追踪编号：" + trace);
    }
}
