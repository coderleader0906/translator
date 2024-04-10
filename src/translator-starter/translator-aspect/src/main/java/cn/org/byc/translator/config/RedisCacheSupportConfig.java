package cn.org.byc.translator.config;

import cn.org.byc.translator.util.TranslatorHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
@ConditionalOnProperty(prefix = "cn.org.byc.translator", name = {"use-redis","useRedis"})
@EnableConfigurationProperties(TranslatorAspectProperties.class)
public class RedisCacheSupportConfig {

    private final TranslatorAspectProperties translatorAspectProperties;

    public RedisCacheSupportConfig(TranslatorAspectProperties translatorAspectProperties){
        this.translatorAspectProperties = translatorAspectProperties;
    }

    @Bean(name = "translatorRedisCacheSupport")
    public TranslatorHelper.CacheSupport translatorRedisCacheSupport(RedisTemplate redisTemplate){
        return new TranslatorHelper.CacheSupport() {
            @Override
            public void put(String cacheKey, String cacheValue) {
                redisTemplate.opsForValue().set(cacheKey, cacheValue, translatorAspectProperties.getCacheExpireTime(), TimeUnit.MINUTES);
            }

            @Override
            public Optional<String> get(String cacheKey) {
                if (StringUtils.hasText(cacheKey) && redisTemplate.hasKey(cacheKey)) {
                    Object value = redisTemplate.opsForValue().get(cacheKey);
                    return value == null ? Optional.empty() : Optional.of(value.toString());
                } else {
                    return Optional.empty();
                }
            }

            @Override
            public boolean isLocalCache() {
                return false;
            }
        };
    }
}
