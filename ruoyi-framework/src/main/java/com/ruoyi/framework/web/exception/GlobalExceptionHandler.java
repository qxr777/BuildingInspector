package com.ruoyi.framework.web.exception;

import javax.servlet.http.HttpServletRequest;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.DemoModeException;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.html.EscapeUtil;
import com.ruoyi.common.utils.security.PermissionUtils;

/**
 * 全局异常处理器
 * 
 * @author ruoyi
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 权限校验异常（ajax请求返回json，redirect请求跳转页面）
     */
    @ExceptionHandler(AuthorizationException.class)
    public Object handleAuthorizationException(AuthorizationException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',权限校验失败'{}'", requestURI, e.getMessage());
        if (ServletUtils.isAjaxRequest(request) || StringUtils.isNotEmpty(request.getHeader("token")))
        {
            return AjaxResult.error(PermissionUtils.getMsg(e.getMessage()));
        }
        else
        {
            return new ModelAndView("error/unauth");
        }
    }

    /**
     * 请求方式不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public AjaxResult handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e,
            HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',不支持'{}'请求", requestURI, e.getMethod());
        return AjaxResult.error(e.getMessage());
    }

    /**
     * 拦截未知的运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public AjaxResult handleRuntimeException(RuntimeException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生未知异常.", requestURI, e);
        return AjaxResult.error(e.getMessage());
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public AjaxResult handleException(Exception e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生系统异常.", requestURI, e);
        return AjaxResult.error(e.getMessage());
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(ServiceException.class)
    public Object handleServiceException(ServiceException e, HttpServletRequest request)
    {
        log.error(e.getMessage(), e);
        if (ServletUtils.isAjaxRequest(request))
        {
            return AjaxResult.error(e.getMessage());
        }
        else
        {
            return new ModelAndView("error/service", "errorMessage", e.getMessage());
        }
    }

    /**
     * 请求路径中缺少必需的路径变量
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public AjaxResult handleMissingPathVariableException(MissingPathVariableException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求路径中缺少必需的路径变量'{}',发生系统异常.", requestURI, e);
        return AjaxResult.error(String.format("请求路径中缺少必需的路径变量[%s]", e.getVariableName()));
    }

    /**
     * 请求参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public AjaxResult handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e,
            HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        String value = Convert.toStr(e.getValue());
        if (StringUtils.isNotEmpty(value))
        {
            value = EscapeUtil.clean(value);
        }
        log.error("请求参数类型不匹配'{}',发生系统异常.", requestURI, e);
        return AjaxResult.error(String.format("请求参数类型不匹配，参数[%s]要求类型为：'%s'，但输入值为：'%s'", e.getName(), e.getRequiredType().getName(), value));
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(BindException.class)
    public AjaxResult handleBindException(BindException e)
    {
        log.error(e.getMessage(), e);
        return AjaxResult.error(toUserFriendlyBindMessage(e));
    }

    /**
     * 将参数绑定/类型转换异常转换为面向用户的中文提示，避免直接暴露底层异常原文。
     */
    private String toUserFriendlyBindMessage(BindException e)
    {
        if (e.getAllErrors() == null || e.getAllErrors().isEmpty())
        {
            return "提交数据有误，请检查后重试";
        }
        String message = e.getAllErrors().get(0).getDefaultMessage();
        if (!isTechnicalTypeConvertMessage(message))
        {
            return StringUtils.isNotEmpty(message) ? message : "提交数据有误，请检查后重试";
        }

        String field = e.getFieldError() != null ? e.getFieldError().getField() : null;
        return buildTypeConvertTip(field, message);
    }

    private boolean isTechnicalTypeConvertMessage(String message)
    {
        if (StringUtils.isEmpty(message))
        {
            return false;
        }
        return message.contains("Failed to convert")
                || message.contains("NumberFormatException")
                || message.contains("nested exception")
                || message.contains("required type")
                || message.contains("java.lang.");
    }

    private String buildTypeConvertTip(String field, String message)
    {
        String fieldName = extractFieldName(field);
        String label = resolveBindFieldLabel(field);
        if (StringUtils.isEmpty(label))
        {
            label = "该字段";
        }

        boolean requireInteger = isIntegerBindField(fieldName)
                || (StringUtils.isNotEmpty(message) && (message.contains("java.lang.Integer")
                        || message.contains("java.lang.Long")
                        || message.contains("required type 'int'")
                        || message.contains("required type 'long'")));

        if (requireInteger)
        {
            if ("angle".equals(fieldName))
            {
                return "角度应为整数";
            }
            return label + "应为整数";
        }
        return label + "请输入有效数字";
    }

    private boolean isIntegerBindField(String fieldName)
    {
        return "angle".equals(fieldName)
                || "quantity".equals(fieldName)
                || "deductPoints".equals(fieldName)
                || "numeratorRatio".equals(fieldName)
                || "denominatorRatio".equals(fieldName)
                || "areaIdentifier".equals(fieldName);
    }

    private String extractFieldName(String field)
    {
        if (StringUtils.isEmpty(field))
        {
            return "";
        }
        int lastDot = field.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < field.length() - 1)
        {
            return field.substring(lastDot + 1);
        }
        return field;
    }

    private String resolveBindFieldLabel(String field)
    {
        if (StringUtils.isEmpty(field))
        {
            return null;
        }
        String name = extractFieldName(field);
        switch (name)
        {
            case "angle":
                return "角度";
            case "length":
            case "lengthRangeStart":
            case "lengthRangeEnd":
                return "长度";
            case "width":
            case "widthRangeStart":
            case "widthRangeEnd":
                return "宽度";
            case "height":
            case "depth":
            case "heightDepth":
            case "heightDepthRangeStart":
            case "heightDepthRangeEnd":
                return "高度/深度";
            case "crackWidth":
            case "crackWidthRangeStart":
            case "crackWidthRangeEnd":
                return "缝宽";
            case "areaLength":
            case "areaWidth":
            case "areaRangeStart":
            case "areaRangeEnd":
                return "面积";
            case "deformation":
            case "deformationRangeStart":
            case "deformationRangeEnd":
                return "变形/位移";
            case "angleRangeStart":
            case "angleRangeEnd":
                return "角度";
            case "quantity":
                return "缺损数量";
            case "deductPoints":
                return "扣分";
            case "numeratorRatio":
            case "denominatorRatio":
                return "比例";
            default:
                return null;
        }
    }

    /**
     * 演示模式异常
     */
    @ExceptionHandler(DemoModeException.class)
    public AjaxResult handleDemoModeException(DemoModeException e)
    {
        return AjaxResult.error("演示模式，不允许操作");
    }
}
