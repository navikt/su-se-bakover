ALTER TABLE revurdering
    ADD COLUMN IF NOT EXISTS
        informasjonSomRevurderes jsonb not null default '{ "Uførhet": "Vurdert", "Inntekt": "Vurdert" }'::jsonb;

ALTER TABLE revurdering
    ALTER COLUMN informasjonSomRevurderes
        DROP DEFAULT;
