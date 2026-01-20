package org.example.authpractice.common.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    // Controller + Service 대상으로 (원하면 repo까지 추가 가능)
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) || within(@org.springframework.stereotype.Service *)")
    public void controllerOrService() {}

    @Around("controllerOrService()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();

        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String className = sig.getDeclaringType().getSimpleName();
        String methodName = sig.getName();

        Map<String, Object> args = maskArgs(sig, pjp.getArgs());

        try {
            Object result = pjp.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("[OK] {}.{} args={} time={}ms", className, methodName, args, elapsed);
            return result;
        } catch (Throwable t) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[EX] {}.{} args={} time={}ms ex={}", className, methodName, args, elapsed, t.toString());
            throw t;
        }
    }

    private Map<String, Object> maskArgs(MethodSignature sig, Object[] values) {
        Parameter[] params = sig.getMethod().getParameters();
        Map<String, Object> map = new LinkedHashMap<>();

        for (int i = 0; i < params.length; i++) {
            String name = params[i].getName(); // -parameters 옵션 없으면 arg0/arg1로 보일 수 있음
            Object v = (values != null && values.length > i) ? values[i] : null;

            // 민감정보 마스킹 룰 (필요한 만큼 추가)
            if (name.toLowerCase().contains("password")) {
                map.put(name, "***");
            } else if (name.toLowerCase().contains("refresh")) {
                map.put(name, "***"); // refresh token 원문 로그 금지
            } else if (name.toLowerCase().contains("authorization")) {
                map.put(name, "***");
            } else {
                map.put(name, safeToString(v));
            }
        }
        return map;
    }

    private String safeToString(Object v) {
        if (v == null) return "null";
        String s = String.valueOf(v);
        if (s.length() > 200) return s.substring(0, 200) + "...";
        return s;
    }
}