ALTER TABLE products
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true;

UPDATE products
SET active = true
WHERE active IS NULL;
