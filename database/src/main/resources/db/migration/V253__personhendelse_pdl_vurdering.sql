ALTER TABLE personhendelse
    ADD COLUMN IF NOT EXISTS pdl_vurdert boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS pdl_relevant boolean,
    ADD COLUMN IF NOT EXISTS pdl_vurdert_tidspunkt timestamptz,
    ADD COLUMN IF NOT EXISTS pdl_snapshot jsonb,
    ADD COLUMN IF NOT EXISTS pdl_diff jsonb;

CREATE INDEX IF NOT EXISTS personhendelse_pdl_vurdering_idx
    ON personhendelse (pdl_vurdert, pdl_relevant, oppgaveId);
