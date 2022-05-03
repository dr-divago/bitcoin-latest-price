CREATE TABLE IF NOT EXISTS bitcoin
(
  id SERIAL,
  price DOUBLE PRECISION,
  price_timestamp timestamptz,
  PRIMARY KEY (id)
);
