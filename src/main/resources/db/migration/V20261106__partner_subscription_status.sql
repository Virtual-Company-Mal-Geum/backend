-- PricingAccount 이전 후 적용한다. 기존 비활성 상태를 권한 없는 INACTIVE로 통합한다.

DO $$
DECLARE
    status_check RECORD;
BEGIN
    -- Hibernate가 생성했거나 이름이 바뀐 status 단독 CHECK도 함께 교체한다.
    FOR status_check IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'status'
        WHERE c.conrelid = 'partner_subscription'::regclass
          AND c.contype = 'c' AND c.conkey = ARRAY[a.attnum]
    LOOP
        EXECUTE format('ALTER TABLE partner_subscription DROP CONSTRAINT %I', status_check.conname);
    END LOOP;
END $$;

UPDATE partner_subscription
SET status = 'INACTIVE'
WHERE status IN ('PENDING_PAYMENT', 'CANCELLED');

ALTER TABLE partner_subscription ADD CONSTRAINT partner_subscription_status_check
    CHECK (status IN ('ACTIVE', 'INACTIVE'));
