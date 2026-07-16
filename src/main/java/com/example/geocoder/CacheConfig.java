package com.example.geocoder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Configuration
public class CacheConfig {

    @Bean
    RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(SerializationPair.fromSerializer(
                        GenericJacksonJsonRedisSerializer.create(builder -> { })));
    }
}