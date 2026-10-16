package com.bookassistant.aop;

import com.bookassistant.service.telemetry.TelemetryService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * AOP aspect that automatically logs all service method executions.
 * <p>
 * <b>What:</b> Intercepts every public method in service classes and logs
 * execution details to telemetry.
 * <p>
 * <b>Why:</b> Eliminates explicit telemetry logging in services. Ensures
 * complete coverage of business logic execution.
 * <p>
 * <b>Where:</b> Executes before/after every service method invocation.
 */
@Aspect
@Component
public class ServiceTelemetryAspect {

    private static final Logger log = LoggerFactory.getLogger(ServiceTelemetryAspect.class);

    private final TelemetryService telemetryService;

    public ServiceTelemetryAspect(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @Before("execution(* com.bookassistant.service..*.*(..)) && !within(com.bookassistant.service.telemetry..*) && !within(com.bookassistant.aop..*)")
    public void logServiceCall(JoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = method.getName();

            telemetryService.logProcedure(
                methodName,
                "service",
                null,
                null,
                Arrays.toString(joinPoint.getArgs()),
                "started",
                null,
                null,
                null,
                0,
                null
            );
        } catch (Exception e) {
            log.debug("Failed to log service call | error={}", e.getMessage());
        }
    }

    @AfterReturning(pointcut = "execution(* com.bookassistant.service..*.*(..)) && !within(com.bookassistant.service.telemetry..*) && !within(com.bookassistant.aop..*)", returning = "result")
    public void logServiceSuccess(JoinPoint joinPoint, Object result) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = signature.getMethod().getName();

            long duration = 0; // Could be enhanced with timing
            telemetryService.logProcedure(
                methodName,
                "service",
                null,
                null,
                Arrays.toString(joinPoint.getArgs()),
                "success",
                result != null ? result.toString() : "void",
                null,
                (int) duration,
                0,
                null
            );
        } catch (Exception e) {
            log.debug("Failed to log service success | error={}", e.getMessage());
        }
    }

    @AfterThrowing(pointcut = "execution(* com.bookassistant.service..*.*(..)) && !within(com.bookassistant.service.telemetry..*) && !within(com.bookassistant.aop..*)", throwing = "ex")
    public void logServiceException(JoinPoint joinPoint, Exception ex) {
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

            telemetryService.logProcedure(
                methodName,
                "service",
                null,
                null,
                Arrays.toString(joinPoint.getArgs()),
                "failure",
                null,
                null,
                null,
                0,
                sw.toString()
            );

            telemetryService.trackError(
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                "service",
                className + "." + methodName,
                sw.toString()
            );
        } catch (Exception e) {
            log.debug("Failed to log service exception | error={}", e.getMessage());
        }
    }
}
