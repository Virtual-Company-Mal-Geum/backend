ALTER TABLE client_order RENAME COLUMN free_scan_usage_month TO free_scan_monthly_base_date;
ALTER TABLE client_order RENAME COLUMN pricing_mode_at_acceptance TO pricing_mode;
ALTER TABLE client_order RENAME COLUMN baseline_order_id TO parent_order_id;
ALTER TABLE pricing_account RENAME COLUMN scan_usage_month TO free_scan_monthly_base_date;
