CREATE TABLE kontrollsamtalenotat_vedlegg (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    kontrollsamtalenotat_id uuid NOT NULL,
    filnavn text NOT NULL,
    mime_type text NOT NULL,
    innhold bytea NOT NULL,
    opprettet timestamptz NOT NULL,
    CONSTRAINT fk_kontrollsamtalenotat_vedlegg_kontrollsamtalenotat
        FOREIGN KEY (kontrollsamtalenotat_id) REFERENCES kontrollsamtale_notat(id)
);

CREATE INDEX idx_kontrollsamtalenotat_vedlegg_kontrollsamtalenotat_id ON kontrollsamtalenotat_vedlegg (kontrollsamtalenotat_id);