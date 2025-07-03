CREATE TABLE stoenad_statistikk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    har_utenlandsopphold TEXT,
    har_familiegjenforening TEXT,
    aar_maaned DATE NOT NULL, -- lagre YearMonth as first day of month
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

CREATE INDEX idx_stoenadstat_sakid ON stoenad_statistikk(sak_id);
CREATE INDEX idx_stoenadstat_fnr ON stoenad_statistikk(personnummer);
CREATE INDEX idx_stoenadstat_fnr_maaned ON stoenad_statistikk(personnummer, aar_maaned);

CREATE TABLE manedsbelop (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stoenad_statistikk_id UUID NOT NULL REFERENCES stoenad_statistikk(id) ON DELETE CASCADE,
    maaned TEXT NOT NULL,
    stonadsklassifisering TEXT,
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