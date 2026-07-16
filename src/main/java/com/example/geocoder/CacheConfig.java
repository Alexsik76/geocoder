package com.example.geocoder;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    RedisCacheConfiguration cacheConfiguration() {
        var typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.geocoder.")
                .build();

        var serializer = GenericJacksonJsonRedisSerializer.create(
                builder -> builder.enableDefaultTyping(typeValidator));

        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }
}