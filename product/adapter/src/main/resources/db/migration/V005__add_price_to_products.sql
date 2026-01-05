-- Add price column to products table
ALTER TABLE products ADD COLUMN price DECIMAL(19, 2) NOT NULL DEFAULT 0.00;

-- Add check constraint for non-negative price
ALTER TABLE products ADD CONSTRAINT price_non_negative CHECK (price >= 0);

-- Comment for documentation
COMMENT ON COLUMN products.price IS 'Product price in KRW (primary currency)';
