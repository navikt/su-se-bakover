CREATE TABLE stonadsklassifisering_dto (
    kode TEXT PRIMARY KEY,
    beskrivelse TEXT NOT NULL
);

CREATE TABLE stoend_statistikk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    har_utenlands_opp_hold TEXT,
    har_familiegjenforening TEXT,
    statistikk_aar_maaned DATE NOT NULL, -- lagre YearMonth as first day of month
    personnummer TEXT NOT NULL,
    personnummer_ektefelle TEXT,
    funksjonell_tid TIMESTAMPTZ NOT NULL,
    teknisk_tid TIMESTAMPTZ NOT NULL,
    stonadstype TEXT NOT NULL,
    sak_id UUID NOT NULL,
    vedtaksdato DATE NOT NULL,
    vedtakstype TEXT NOT NULL,
    vedtaksresultat TEXT NOT NULL,
    behandlende_enhet_kode TEXT NOT NULL,
    ytelse_virkningstidspunkt DATE NOT NULL,
    gjeldende_stonad_virkningstidspunkt DATE NOT NULL,
    gjeldende_stonad_stopptidspunkt DATE NOT NULL,
    gjeldende_stonad_utbetalingsstart DATE NOT NULL,
    gjeldende_stonad_utbetalingsstopp DATE NOT NULL,
    opphorsgrunn TEXT,
    opphorsdato DATE,
    flyktningsstatus TEXT,
    versjon TEXT
);

CREATE TABLE manedsbelop (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stoend_statistikk_id UUID NOT NULL REFERENCES stoend_statistikk(id) ON DELETE CASCADE,
    maaned TEXT NOT NULL,
    stonadsklassifisering TEXT NOT NULL REFERENCES stonadsklassifisering_dto(kode),
    bruttosats BIGINT NOT NULL,
    nettosats BIGINT NOT NULL,
    fradrag_sum BIGINT NOT NULL
);

CREATE TABLE inntekt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    manedsbelop_id UUID NOT NULL REFERENCES manedsbelop(id) ON DELETE CASCADE,
    inntektstype TEXT NOT NULL,
    belop BIGINT NOT NULL,
    tilhorer TEXT NOT NULL,
    er_utenlandsk BOOLEAN NOT NULL
);