package cn.org.byc.translator.aspect;

import cn.org.byc.translator.util.TranslatorHelper;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class TranslatorAspect {

    private final TranslatorHelper translatorHelper;

    public TranslatorAspect(TranslatorHelper translatorHelper) {
        this.translatorHelper = translatorHelper;
    }

    @Pointcut("@annotation(cn.org.byc.translator.annotation.TranslatorReturn)")
    private void pointCut() {
    }

    @AfterReturning(value = "pointCut()", returning = "result")
    public void doAfter(JoinPoint joinPoint, Object result) {
        translatorHelper.startTrans(result);
    }
}
