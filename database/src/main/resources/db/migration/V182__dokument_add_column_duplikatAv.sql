ALTER TABLE
    dokument
    ADD COLUMN IF NOT EXISTS duplikatAv uuid;

ALTER TABLE dokument
    ADD CONSTRAINT fk_duplikatAv
        FOREIGN KEY (duplikatAv) REFERENCES dokument (id);

COMMENT ON COLUMN dokument.duplikatAv IS 'Dokumentet er et duplikat av et annet dokument og skal ignoreres ved søk og visning.';

-- Setter det første dokumentet til å være duplikat av det siste dokumentet for et enkelt vedtak.
update dokument set duplikatAv = '7202a89f-ef54-4fad-899f-6ddd32d74c55' where id = '527e84d6-c8a5-49d7-941a-d9ca2dca866e';
update dokument set duplikatAv = '1b34fdc6-7b85-4a57-ab99-df756c6724d4' where id = '3b3e897a-8b4e-42db-a79c-48dd872c7bcd';
update dokument set duplikatAv = '1244d833-fc5f-49bf-b9d6-ad87b1a51c16' where id = '9a4bdf0d-ee1a-4614-8747-ecf31adb0a35';
