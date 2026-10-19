ALTER TABLE historisk_alder_manedsbelop
    ADD COLUMN fradragskoder TEXT[] NOT NULL DEFAULT '{}'::TEXT[];

ALTER TABLE historisk_alder_stonad
    ADD COLUMN opphorskode_raw TEXT,
    ADD COLUMN opphorsgrunn TEXT CHECK (
        opphorsgrunn IN (
            'ANNULLERT',
            'ALDERSPENSJON',
            'ANNEN_ÅRSAK',
            'FLYTTET',
            'HØY_INNTEKT',
            'INSTITUSJON',
            'LANGT_UTENLANDSOPPHOLD',
            'STOR_FORMUE',
            'FLYTTET_TIL_UTLANDET',
            'DØD',
            'UTENLANDSK_ADRESSE_ELLER_GIRONUMMER'
        )
    ),
    ADD COLUMN oppdrag_id TEXT;

ALTER TABLE historisk_alder_vedtak
    ADD COLUMN endringskoder TEXT[] NOT NULL DEFAULT '{}'::TEXT[],
    ADD COLUMN kontornummer TEXT,
    ADD COLUMN saksblokk TEXT,
    ADD COLUMN saksnummer TEXT,
    ADD COLUMN behandlende_kontor TEXT,
    ADD COLUMN sendt_til_os TIMESTAMP,
    ADD COLUMN mottatt_fra_os TIMESTAMP,
    ADD COLUMN godkjent_av_os TEXT,
    ADD COLUMN revurderingsdato DATE;
