package com.example.geocoder;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

@Component
public class CacheInspector {

    private static final String KEY_PATTERN = "geocoding::*";
    private static final int LIMIT = 20;

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheInspector(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<GeocodingResult> latestEntries() {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PATTERN).count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext() && keys.size() < LIMIT) {
                keys.add(cursor.next());
            }
        }

        List<GeocodingResult> entries = new ArrayList<>();
        for (String key : keys) {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof GeocodingResult result) {
                entries.add(result);
            }
        }
        return entries;
    }
}