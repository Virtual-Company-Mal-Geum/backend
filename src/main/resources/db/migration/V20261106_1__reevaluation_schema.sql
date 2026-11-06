-- Also supports existing databases where Hibernate already added these columns.
ALTER TABLE client_order ADD COLUMN IF NOT EXISTS with_improvement BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE client_order ADD COLUMN IF NOT EXISTS baseline_order_id BIGINT;
ALTER TABLE analysis_job ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP(6);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'baseline_order_id'
        WHERE c.conrelid = 'client_order'::regclass
          AND c.confrelid = 'client_order'::regclass
          AND c.contype = 'f' AND c.conkey = ARRAY[a.attnum]
    ) THEN
        ALTER TABLE client_order ADD CONSTRAINT client_order_baseline_fk
            FOREIGN KEY (baseline_order_id) REFERENCES client_order(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS client_order_baseline_idx ON client_order(baseline_order_id);
