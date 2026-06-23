CREATE TABLE notat (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sakid uuid NOT NULL,
    referanseid uuid NOT NULL,
    notat text NOT NULL,
    opprettet timestamptz NOT NULL,
    endret timestamptz NOT NULL,
    saksbehandler jsonb NOT NULL,
    CONSTRAINT fk_notat_sak
        FOREIGN KEY (sakid) REFERENCES sak(id)
);

CREATE INDEX idx_notat_sakid ON notat (sakid);

CREATE TABLE notat_vedlegg (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    notat_id uuid NOT NULL,
    filnavn text NOT NULL,
    innhold bytea NOT NULL,
    opprettet timestamptz NOT NULL,
    CONSTRAINT fk_notat_vedlegg_notat
        FOREIGN KEY (notat_id) REFERENCES notat(id)
);

CREATE INDEX idx_notat_vedlegg_notat_id ON notat_vedlegg (notat_id);
