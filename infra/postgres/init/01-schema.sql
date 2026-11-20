CREATE ROLE debezium
  WITH LOGIN REPLICATION PASSWORD 'dbz_dev_only';

CREATE TABLE public.customers (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.orders (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES public.customers(id),
    status VARCHAR(30) NOT NULL,
    total_amount NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO public.customers (full_name, email) VALUES
    ('Aman Sharma', 'aman@example.com'),
    ('Meera Patel', 'meera@example.com');

INSERT INTO public.orders (customer_id, status, total_amount) VALUES
    (1, 'PROCESSING', 1499.00),
    (2, 'SHIPPED', 799.50);

GRANT CONNECT ON DATABASE shadowbase_prod TO debezium;
GRANT USAGE ON SCHEMA public TO debezium;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO debezium;

CREATE PUBLICATION shadowbase_publication
    FOR TABLE public.customers, public.orders;