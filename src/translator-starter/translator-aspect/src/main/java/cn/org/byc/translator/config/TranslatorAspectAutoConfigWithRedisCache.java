package cn.org.byc.translator.config;

import cn.org.byc.translator.aspect.TranslatorAspect;
import cn.org.byc.translator.util.TranslatorHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(TranslatorAspectProperties.class)
@ConditionalOnBean(name = {"translatorRedisCacheSupport"})
@Import(RedisCacheSupportConfig.class)
public class TranslatorAspectAutoConfigWithRedisCache {

    private final TranslatorAspectProperties translatorAspectProperties;

    public TranslatorAspectAutoConfigWithRedisCache(TranslatorAspectProperties translatorAspectProperties) {
        this.translatorAspectProperties = translatorAspectProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({DataSource.class})
    public TranslatorHelper translatorHelper(DataSource dataSource,
                                             TranslatorHelper.CacheSupport redisCacheSupport) {
        return new TranslatorHelper(dataSource,
                translatorAspectProperties.getDictQuerySql(),
                translatorAspectProperties.getDictQuerySqlEng(),
                redisCacheSupport);
    }


    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({TranslatorHelper.class})
    public TranslatorAspect translatorAspect(TranslatorHelper translatorHelper) {
        return new TranslatorAspect(translatorHelper);
    }
}
