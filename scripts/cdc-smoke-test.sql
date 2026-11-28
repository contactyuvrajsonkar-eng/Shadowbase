\set ON_ERROR_STOP on

INSERT INTO public.customers (full_name, email)
VALUES ('CDC Test Customer', 'cdc-test-' || txid_current() || '@example.com')
RETURNING id AS customer_id \gset

INSERT INTO public.orders (customer_id, status, total_amount)
VALUES (:customer_id, 'CDC_TEST', 12.34)
RETURNING id AS order_id \gset

UPDATE public.orders
SET status = 'CDC_UPDATED'
WHERE id = :order_id;

DELETE FROM public.orders
WHERE id = :order_id;

DELETE FROM public.customers
WHERE id = :customer_id;