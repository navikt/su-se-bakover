UPDATE mottaker
SET brevtype = 'VEDTAK'
WHERE brevtype = 'VEDTAKSBREV';

ALTER TABLE mottaker
    ALTER COLUMN brevtype SET DEFAULT 'VEDTAK';
