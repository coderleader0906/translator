package cn.org.byc.translator.config;

import cn.org.byc.translator.aspect.TranslatorAspect;
import cn.org.byc.translator.util.TranslatorHelper;
import cn.org.byc.translator.util.TranslatorHelper.CacheSupport;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(TranslatorAspectProperties.class)
@AutoConfigureAfter(TranslatorAspectAutoConfigWithRedisCache.class)
public class TranslatorAspectAutoConfigWithCaffeineCache {
    private final TranslatorAspectProperties translatorAspectProperties;


    public TranslatorAspectAutoConfigWithCaffeineCache(TranslatorAspectProperties translatorAspectProperties) {
        this.translatorAspectProperties = translatorAspectProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({DataSource.class})
    public TranslatorHelper translatorHelper(DataSource dataSource) {
        Cache<String, Optional<String>> cache = Caffeine.newBuilder()
                .expireAfterWrite(translatorAspectProperties.getCacheExpireTime(), TimeUnit.MINUTES)
                .initialCapacity(128)
                .maximumSize(2048)
                .build();

        TranslatorHelper.CacheSupport cacheSupport = new TranslatorHelper.CacheSupport() {
            @Override
            public void put(String cacheKey, String cacheValue) {
                cache.put(cacheKey, Optional.ofNullable(cacheValue));
            }

            @Override
            public Optional<String> get(String cacheKey) {
                return cache.getIfPresent(cacheKey);
            }
        };

        return new TranslatorHelper(dataSource,
                translatorAspectProperties.getDictQuerySql(),
                translatorAspectProperties.getDictQuerySqlEng(),
                cacheSupport);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({TranslatorHelper.class})
    public TranslatorAspect translatorAspect(TranslatorHelper translatorHelper) {
        return new TranslatorAspect(translatorHelper);
    }
}
