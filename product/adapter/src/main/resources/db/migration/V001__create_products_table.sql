-- Products table
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    stock INTEGER NOT NULL CHECK (stock >= 0),
    initial_stock INTEGER NOT NULL CHECK (initial_stock >= 0),
    sale_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,

    CONSTRAINT stock_not_negative CHECK (stock >= 0),
    CONSTRAINT initial_stock_immutable CHECK (stock <= initial_stock)
);

-- Indexes for product queries
CREATE INDEX idx_product_sale_date ON products(sale_date);
CREATE INDEX idx_product_status_date ON products(sale_date, stock);

-- Comments for documentation
COMMENT ON TABLE products IS 'Product catalog with stock and sale date management';
COMMENT ON COLUMN products.stock IS 'Current available stock (includes unsold + active slots)';
COMMENT ON COLUMN products.initial_stock IS 'Original stock quantity (immutable after creation)';
COMMENT ON COLUMN products.created_by IS 'FK to auth.users (external domain)';
