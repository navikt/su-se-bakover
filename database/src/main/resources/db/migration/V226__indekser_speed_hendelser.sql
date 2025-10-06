CREATE INDEX IF NOT EXISTS idx_hendelse_behandlingsid
    ON hendelse ((data->'kravgrunnlag'->>'behandlingsId'));

CREATE INDEX IF NOT EXISTS idx_hendelse_ekstern_tidspunkt_text
    ON hendelse ((data->'kravgrunnlag'->>'eksternTidspunkt'));

CREATE INDEX IF NOT EXISTS idx_hendelse_ekstern_vedtak_id
    ON hendelse ((data->'kravgrunnlag'->>'eksternVedtakId'));

CREATE INDEX IF NOT EXISTS idx_hendelse_type_kravgrunnlag
    ON hendelse (type)
    WHERE data ?? 'kravgrunnlag';


CREATE INDEX IF NOT EXISTS idx_hendelse_kravgrunnlag_vedtak_tidspunkt
    ON hendelse (
                 (data->'kravgrunnlag'->>'eksternVedtakId'),
                 ((data->'kravgrunnlag'->>'eksternTidspunkt'))
        )
    WHERE type = 'KNYTTET_KRAVGRUNNLAG_TIL_SAK'
        AND data ? 'kravgrunnlag';

CREATE INDEX IF NOT EXISTS idx_hendelse_type_status ON hendelse(type) WHERE data ? 'status';
CREATE INDEX IF NOT EXISTS idx_hendelse_eksternVedtakId ON hendelse((data->>'eksternVedtakId'));
CREATE INDEX IF NOT EXISTS idx_hendelse_eksternTidspunkt ON hendelse(((data->>'eksternTidspunkt')));
CREATE INDEX IF NOT EXISTS idx_hendelse_behandlingsId_versjon ON hendelse ((data->>'behandlingsId'), versjon);
CREATE INDEX IF NOT EXISTS idx_kravgrunnlag_status ON hendelse (((data -> 'kravgrunnlag' ->> 'status')));

-- Disse optimaliseringene gjør henting fra hendelser er mulig uten timeout. Avg før var 32s nå er det 320 ms