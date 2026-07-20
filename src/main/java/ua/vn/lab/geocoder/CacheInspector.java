package ua.vn.lab.geocoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

@Component
public class CacheInspector {

    private static final Logger log = LoggerFactory.getLogger(CacheInspector.class);

    private static final String KEY_PATTERN = "geocoding::*";
    private static final int LIMIT = 20;

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheInspector(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<Location> latestEntries() {
        try {
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions().match(KEY_PATTERN).count(100).build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext() && keys.size() < LIMIT) {
                    keys.add(cursor.next());
                }
            }

            List<Location> entries = new ArrayList<>();
            for (String key : keys) {
                Object value = redisTemplate.opsForValue().get(key);
                if (value instanceof Location location) {
                    entries.add(location);
                }
            }
            return entries;
        } catch (RuntimeException e) {
            log.warn("Cache inspection failed, showing an empty cache table: {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            log.debug("Cache inspection failure details", e);
            return Collections.emptyList();
        }
    }
}