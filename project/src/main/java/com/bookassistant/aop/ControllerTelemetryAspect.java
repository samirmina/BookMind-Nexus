package com.bookassistant.aop;

import com.bookassistant.service.telemetry.TelemetryService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * AOP aspect that automatically logs all controller method executions.
 * <p>
 * <b>What:</b> Intercepts every @RequestMapping, @GetMapping, @PostMapping, etc.
 * in the controller layer and logs the execution to telemetry.
 * <p>
 * <b>Why:</b> Eliminates the need for explicit telemetryService.logEvent() calls
 * in every controller method. Ensures 100% coverage of web endpoints.
 * <p>
 * <b>Where:</b> Executes before/after every controller method invocation.
 */
@Aspect
@Component
public class ControllerTelemetryAspect {

    private static final Logger log = LoggerFactory.getLogger(ControllerTelemetryAspect.class);

    private final TelemetryService telemetryService;

    public ControllerTelemetryAspect(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @Before("execution(* com.bookassistant.controller..*.*(..)) && !within(com.bookassistant.controller.web.OutputsController) && !within(com.bookassistant.service.telemetry..*) && !within(com.bookassistant.aop..*)")
    public void logControllerCall(JoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = method.getName();

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            String requestUri = attributes != null ? attributes.getRequest().getRequestURI() : "unknown";
            String httpMethod = attributes != null ? attributes.getRequest().getMethod() : "unknown";

            telemetryService.logEvent(
                "controller",
                "method_call",
                "debug",
                null,
                null,
                null,
                className,
                null,
                String.format("%s.%s called", className, methodName),
                String.format("uri=%s, method=%s, args=%s", requestUri, httpMethod, Arrays.toString(joinPoint.getArgs())),
                null,
                null
            );
        } catch (Exception e) {
            log.debug("Failed to log controller call | error={}", e.getMessage());
        }
    }

    @AfterReturning(pointcut = "execution(* com.bookassistant.controller..*.*(..)) && !within(com.bookassistant.service.telemetry..*) && !within(com.bookassistant.aop..*)", returning = "result")
    public void logControllerSuccess(JoinPoint joinPoint, Object result) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = signature.getMethod().getName();

            if (result instanceof String resultStr && resultStr.startsWith("redirect:")) {
                telemetryService.logEvent(
                    "controller",
                    "redirect",
                    "info",
                    null,
                    null,
                    null,
                    className,
                    null,
                    String.format("%s.%s redirected to %s", className, methodName, resultStr),
                    resultStr,
                    null,
                    null
                );
            }
        } catch (Exception e) {
            log.debug("Failed to log controller success | error={}", e.getMessage());
        }
    }

    @AfterThrowing(pointcut = "execution(* com.bookassistant.controller..*.*(..)) && !within(com.bookassistant.service.telemetry..*) && !within(com.bookassistant.aop..*)", throwing = "ex")
    public void logControllerException(JoinPoint joinPoint, Exception ex) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = signature.getMethod().getName();

            java.io.StringWriter sw = new java.io.StringWriter();
            ex.printStackTrace(new java.io.PrintWriter(sw));

            if (ex instanceof IllegalArgumentException && ex.getMessage() != null && ex.getMessage().contains("Illegal group reference")) {
                log.error("IllegalArgumentException: Illegal group reference | className={} | methodName={} | args={} | stackTrace={}",
                    className, methodName, Arrays.toString(joinPoint.getArgs()), sw.toString());
            }

            telemetryService.logEvent(
                "controller",
                "exception",
                "error",
                null,
                null,
                null,
                className,
                null,
                String.format("%s.%s threw %s", className, methodName, ex.getClass().getSimpleName()),
                ex.getMessage(),
                sw.toString(),
                null
            );

            telemetryService.trackError(
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                "controller",
                className + "." + methodName,
                sw.toString()
            );
        } catch (Exception e) {
            log.debug("Failed to log controller exception | error={}", e.getMessage());
        }
    }
}
