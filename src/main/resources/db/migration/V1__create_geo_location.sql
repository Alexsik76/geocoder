CREATE TABLE geo_location (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    address   VARCHAR(512) NOT NULL UNIQUE,
    latitude  DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
);

INSERT INTO geo_location (address, latitude, longitude) VALUES
    ('Kyiv, Ukraine', 50.4501, 30.5234),
    ('Lviv, Ukraine', 49.8397, 24.0297),
    ('Vinnytsia, Ukraine', 49.2331, 28.4682);