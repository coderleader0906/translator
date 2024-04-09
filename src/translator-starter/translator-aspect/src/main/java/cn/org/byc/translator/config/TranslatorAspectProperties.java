package cn.org.byc.translator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cn.org.byc.translator")
public class TranslatorAspectProperties {


    private String dictQuerySql =
            "select dict_display from dict_data where dict_code = '%s' and dict_data_code = ?";

    private String dictQuerySqlEng =
            "select dict_eng_display from dict_data where dict_code = '%s' and dict_data_code = ?";


    private Integer cacheExpireTime = 30;

    public String getDictQuerySql() {
        return dictQuerySql;
    }

    public void setDictQuerySql(String dictQuerySql) {
        this.dictQuerySql = dictQuerySql;
    }

    public String getDictQuerySqlEng() {
        return dictQuerySqlEng;
    }

    public void setDictQuerySqlEng(String dictQuerySqlEng) {
        this.dictQuerySqlEng = dictQuerySqlEng;
    }

    public Integer getCacheExpireTime() {
        return cacheExpireTime;
    }

    public void setCacheExpireTime(Integer cacheExpireTime) {
        this.cacheExpireTime = cacheExpireTime;
    }
}
