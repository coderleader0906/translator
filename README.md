# translator

## 1. 引入依赖
```xml
<dependencies>
    <!-- 注解 -->
    <dependency>
        <groupId>cn.org.byc</groupId>
        <artifactId>translator-annotation</artifactId>
        <version>0.0.1</version>
    </dependency>
    
    <!-- 切面 -->
    <dependency>
        <groupId>cn.org.byc</groupId>
        <artifactId>translator-aspect</artifactId>
        <version>0.0.1</version>
    </dependency>
</dependencies>
```

---
> 如果自动配置失败，可尝试在`ComponentScan`添加属性 `excludeFilters = {@ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn\\.org\\.byc\\.translator.*TranslatorAspectAutoConfig.*")`
---

## 2. 配置项
```yaml
cn:
  org:
    byc:
      translator:
        # 使用redis做缓存。如果项目使用了RedisTemplate, 且此属性为true, 才会使用redis, 否则使用caffeine
        # 可以不配置, 默认使用caffeine
        use-redis: true
        # 如果@TransField使用了dictCode，则自动使用此sql模版
        # 默认值:select dict_display from dict_data where dict_code = '%s' and dict_data_code = ?
        dictQuerySql:
        # 默认值:select dict_eng_display from dict_data where dict_code = '%s' and dict_data_code = ?
        dictQuerySqlEng:
        # 可以不配置, 缓存写入后的失效时间，单位为分钟
        # 默认值:30
        cacheExpireTime:
```

## 3. 注解使用说明

### 3.1 @TranslatorField: sql模板带参数
> 该使用方式可以满足大多数应用场景，包括关联查询
```java
@Data
class DemoVO{
    /**
     * SQL模板
     */
    private String SQL_COMMON = "select {0} from {1} where {2} = ?";

    private Long id;
    private String code;
    
    @TransField(associateField = "id",
            cacheKeyPrefix = "id_to_name:",
            sqlTemplateParam = {"column", "table_name", "id"},
            sql = SQL_COMMON)
    private String idToName;
    
    @TransField(associateField = "code",
            cacheKeyPrefix = "code_to_name:",
            sqlTemplateParam = {"column", "table_name", "code"},
            sql = SQL_COMMON)
    private String schoolEngName;
}
```

### 3.2 @TranslatorField: 字典翻译
```java
class DemoVO{
    private String cardType;
    
    @TransField(associateField = "cardType", dictCode = "card")
    private String cardTypeName;
}
```

### 3.3 @TranslatorReturn
> 此注解用来做切面的pointcut,加载需要转译的返参的方法上面

### 3.4 @TransNested
> 如果DemoVO_B非集合类型且需要转译，需要加上@TransNested
```java
class DemoVO_A{
    @TransNested
    private DemoVO_B vob;
}
```

## 4. 其他特性
### 4.1 支持容器内元素的注入
比如DemoVO有字段需要转译，返参List<DemoVO>, Map<Integer,DemoVO>里的DemoVO也能正常转译

### 4.2 支持继承
```java
@Data
class SuperClass{
    private Integer gender;
    @TransField(associateField = "gender", dictCode = "gender")
    private String genderName;    

    private String cardType;
}
@Data
class SubClass extends SuperClass{
    @TransField(associateField = "cardType", dictCode = "card")
    private String cardName; 
}
```