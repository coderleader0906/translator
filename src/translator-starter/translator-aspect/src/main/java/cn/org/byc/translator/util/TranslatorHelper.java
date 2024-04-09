package cn.org.byc.translator.util;

import cn.org.byc.translator.annotation.TranslatorField;
import cn.org.byc.translator.annotation.TranslatorNested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


public class TranslatorHelper {

    private static final Logger log = LoggerFactory.getLogger(TranslatorHelper.class);


    private final HashMap<Class<?>, RegisterInfo> REGISTER_INFO_MAP = new HashMap<>(32);

    private final ThreadLocal<Map<String, String>> DATA_CACHE_TL = new ThreadLocal<>();

    private DataSource dataSource;

    private String dictQuerySql;

    private String dictQuerySqlEng;

    private CacheSupport cacheSupport;

    public TranslatorHelper(DataSource dataSource, String dictQuerySql, String dictQuerySqlEng, CacheSupport cacheSupport) {
        this.dataSource = dataSource;
        this.dictQuerySql = dictQuerySql;
        this.dictQuerySqlEng = dictQuerySqlEng;
        this.cacheSupport = cacheSupport;
    }

    public void startTrans(Object result){
        if (result == null){
            return;
        }

        if (!cacheSupport.isLocalCache()){
            try {
                DATA_CACHE_TL.set(new HashMap<>());
                handle(result);
            } finally {
                DATA_CACHE_TL.remove();
            }
        } else {
            handle(result);
        }
    }

    /**
     * 区分是否集合分别进行处理
     *
     * @param result
     */
    public void handle(Object result) {
        if (result instanceof Map map) {
            map.values().forEach(this::handleSimple);
        } else if (result instanceof Collection collection) {
            collection.forEach(this::handleSimple);
        } else {
            handleSimple(result);
        }
    }

    /**
     * 处理非集合类
     *
     * @param obj
     */
    public void handleSimple(Object obj) {
        if (obj == null
                || ClassUtils.isPrimitiveOrWrapper(obj.getClass())
                || String.class.isAssignableFrom(obj.getClass())) {
            return;
        }

        Class<?> objType = obj.getClass();
        try {
            ensureRegistered(objType);
            RegisterInfo registerInfo = REGISTER_INFO_MAP.get(objType);
            registerInfo.translatorList.forEach(e -> e.transAndSetValue(obj));
            registerInfo.transNestList.forEach(e -> {
                try {
                    handle(e.get(obj));
                } catch (IllegalAccessException illegalAccessException) {
                    log.error(illegalAccessException.getMessage(), e);
                }
            });
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 确保类的转译信息已经注册
     *
     * @param type
     * @throws Exception
     */
    private void ensureRegistered(Class<?> type) {
        if (REGISTER_INFO_MAP.containsKey(type)) {
            return;
        }
        synchronized (REGISTER_INFO_MAP) {
            registerClass(type);
        }
    }

    /**
     * 注册类的转译信息
     *
     * @param type
     * @throws Exception
     */
    private void registerClass(Class<?> type) {
        Class<?> superClass = type.getSuperclass();

        // 注册父类
        if (!Object.class.equals(superClass)) {
            if (!REGISTER_INFO_MAP.containsKey(superClass)) {
                registerClass(superClass);
            }
        }

        List<Translator> translatorList = new ArrayList<>();
        List<Field> transNestedList = new ArrayList<>();

        try {
            Map<String, PropertyDescriptor> propertyDescriptorMap =
                    Arrays.stream(Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors())
                            .collect(Collectors.toMap(PropertyDescriptor::getName, Function.identity()));
            for (Field field : type.getDeclaredFields()) {
                registerField(type, field, propertyDescriptorMap, translatorList, transNestedList);
            }
            // 把父类的注册信息复制过来,让转译字段能被继承
            RegisterInfo superClassRegisterInfo = REGISTER_INFO_MAP.get(superClass);
            if (superClassRegisterInfo != null) {
                translatorList.addAll(superClassRegisterInfo.translatorList);
                transNestedList.addAll(superClassRegisterInfo.transNestList);
            }
        } catch (Exception e) {
            log.error("类型:" + type.getName() + "注册转译信息失败", e);
        }
        REGISTER_INFO_MAP.put(type, new RegisterInfo(translatorList, transNestedList));
    }

    /**
     * 解析类的属性，构造转译信息
     *
     * @param type                  类
     * @param field                 字段
     * @param propertyDescriptorMap 类的属性描述符
     * @param translatorList        field解析出来的字段转译器，放这里
     * @param transNestedList       field是一个复杂类型，需要进一步解析，放这里
     */
    private void registerField(Class<?> type,
                               Field field,
                               Map<String, PropertyDescriptor> propertyDescriptorMap,
                               Collection<Translator> translatorList,
                               List<Field> transNestedList) {
        try {
            if (field.isAnnotationPresent(TranslatorField.class)) {
                TranslatorField transField = field.getAnnotation(TranslatorField.class);
                String[] associateFieldName = transField.associateField();

                Method destFieldWriteMethod = propertyDescriptorMap.get(field.getName()).getWriteMethod();
                Method[] srcFieldReadMethods = Arrays.stream(associateFieldName).map(fieldName -> {
                    PropertyDescriptor fieldPd = propertyDescriptorMap.get(fieldName);
                    Assert.notNull(fieldPd, String.format("属性转义注册失败,类型:%s,转义字段:%s的关联字段:%s不存在",
                            type.getSimpleName(), field.getName(), fieldName));
                    return fieldPd.getReadMethod();
                }).toArray(Method[]::new);

                String dictCode = transField.dictCode();
                String sql = transField.sql();
                String cacheKeyPrefix = transField.cacheKeyPrefix();
                if (isEmpty(dictCode) && isEmpty(sql)) {
                    log.error("属性转义注册失败，类型：{}，转义字段：{}未指定查询方式", type.getSimpleName(), field.getName());
                } else {
                    if (!isEmpty(dictCode)) {
                        // 约定：如果字段名以enName或engName结尾（不区分大小写)则自动使用字典的英文名注入字段
                        if (StringUtils.endsWithIgnoreCase(field.getName(), "enName") ||
                                StringUtils.endsWithIgnoreCase(field.getName(), "engName")) {
                            sql = String.format(dictQuerySqlEng, dictCode);
                            if (isEmpty(cacheKeyPrefix)) {
                                cacheKeyPrefix = "dict:" + dictCode + ":";
                            }
                        } else {
                            sql = String.format(dictQuerySql, dictCode);
                            if (isEmpty(cacheKeyPrefix)) {
                                cacheKeyPrefix = "dict:eng:" + dictCode + ":";
                            }
                        }
                    } else {
                        String[] sqlTemplateParam = transField.sqlTemplateParam();
                        if (sqlTemplateParam.length > 0) {
                            sql = MessageFormat.format(sql, (Object[]) sqlTemplateParam);
                        }
                    }
                    Translator translator = new Translator(destFieldWriteMethod,
                            srcFieldReadMethods,
                            sql,
                            cacheKeyPrefix);
                    translatorList.add(translator);
                }

            } else if (field.isAnnotationPresent(TranslatorNested.class)) {
                field.setAccessible(true);
                transNestedList.add(field);
                // 容器类型也注册一下
            } else if ((List.class.isAssignableFrom(field.getType())) || Map.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                transNestedList.add(field);
            }
        } catch (Exception e) {
            log.error(String.format("类型:%s, 解析属性:%s转译信息失败", type.getName(), field.getName()), e);
        }
    }

    /**
     * 缓存一个类的转译信息
     */
    class RegisterInfo {
        List<Translator> translatorList;
        List<Field> transNestList;

        public RegisterInfo(List<Translator> translatorList, List<Field> transNestList) {
            this.translatorList = translatorList;
            this.transNestList = transNestList;
        }
    }

    class Translator {
        final private Method fieldWriteMethod;
        final private Method[] associateFieldReadMethod;
        final private String sql;
        final private String cacheKeyPrefix;

        public Translator(Method fieldWriteMethod, Method[] associateFieldReadMethod, String sql, String cacheKey) {
            this.fieldWriteMethod = fieldWriteMethod;
            this.associateFieldReadMethod = associateFieldReadMethod;
            this.cacheKeyPrefix = cacheKey;
            this.sql = sql;
        }

        public void transAndSetValue(Object obj) {
            try {
                Object[] associateFieldValue = new Object[associateFieldReadMethod.length];
                // 如果有字段为空，就跳过翻译
                for (int i = 0; i < associateFieldReadMethod.length; i++) {
                    Object readFieldValue = associateFieldReadMethod[i].invoke(obj);
                    if (readFieldValue == null ||
                            (readFieldValue instanceof String && ((String) readFieldValue).trim().isEmpty())) {
                        return;
                    }
                    associateFieldValue[i] = readFieldValue;
                }

                String cacheKey =
                        cacheKeyPrefix + Arrays.stream(associateFieldValue).map(Object::toString).collect(Collectors.joining(":"));
                String descFieldValue = get(cacheKey, associateFieldValue);
                if (descFieldValue != null) {
                    fieldWriteMethod.invoke(obj, descFieldValue);
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                log.error(e.getMessage(), e);
            }
        }

        private String get(String cacheKey, Object[] param) {
            String value = null;
            boolean setLocalCache = false;
            if (DATA_CACHE_TL.get() != null) {
                // ThreadLocal缓存
                value = DATA_CACHE_TL.get().get(cacheKey);
                setLocalCache = true;
            }

            boolean setCache = true;
            if (value == null) {
                // 缓存
                Optional<String> valueOptional = cacheSupport.get(cacheKey);
                if (valueOptional.isEmpty()) {
                    value = getFromDB(param);
                } else {
                    value = valueOptional.orElse(null);
                    setCache = false;
                }
            } else {
                setLocalCache = false;
            }

            if (value == null) {
                log.warn("cache_value_is_null, cacheKey = {}", cacheKey);
            }

            if (setLocalCache) {

                DATA_CACHE_TL.get().put(cacheKey, value);
            }
            if (setCache) {
                cacheSupport.put(cacheKey, value);
            }
            return value;
        }

        private String getFromDB(Object[] param) {
            try (Connection connection = dataSource.getConnection()) {
                PreparedStatement preparedStatement = connection.prepareStatement(sql);
                for (int i = 0; i < param.length; i++) {
                    preparedStatement.setObject(i + 1, param[i]);
                }
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    return resultSet.getString(1);
                }
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
            return null;
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || "".equals(s.trim());
    }


    public interface CacheSupport {
        void put(String cacheKey, String cacheValue);
        Optional<String> get(String cacheKey);

        default boolean isLocalCache(){
            return true;
        }
    }
}
