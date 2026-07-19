DELETE FROM geo_location
WHERE id NOT IN (
    SELECT MIN(id)
    FROM geo_location
    GROUP BY trim(regexp_replace(lower(translate(address, ',.', '  ')), '\s+', ' ', 'g'))
);

UPDATE geo_location
SET address = trim(regexp_replace(lower(translate(address, ',.', '  ')), '\s+', ' ', 'g'))
WHERE address <> trim(regexp_replace(lower(translate(address, ',.', '  ')), '\s+', ' ', 'g'));
