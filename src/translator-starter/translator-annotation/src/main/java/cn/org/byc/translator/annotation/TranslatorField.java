package cn.org.byc.translator.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TranslatorField {

    /**
     * 关联字段
     *
     * @return
     */
    String[] associateField();

    /**
     * 数据字典CODE
     *
     * @return
     */
    String dictCode() default "";

    /**
     * SQL 参数模板，使用MessageFormat格式化sql模版
     *
     * @return
     */
    String[] sqlTemplateParam() default {};

    /**
     * SQL模板
     *
     * @return
     */
    String sql();

    /**
     * 缓存Key的前缀
     *
     * @return
     */
    String cacheKeyPrefix() default "";
}
