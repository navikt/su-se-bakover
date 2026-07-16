CREATE TABLE kontrollsamtale_notat_vedlegg (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    kontrollsamtale_notat_id uuid NOT NULL,
    filnavn text NOT NULL,
    mime_type text NOT NULL,
    innhold bytea NOT NULL,
    opprettet timestamptz NOT NULL,
    CONSTRAINT fk_kontrollsamtale_notat_vedlegg_kontrollsamtale_notat
        FOREIGN KEY (kontrollsamtale_notat_id) REFERENCES kontrollsamtale_notat(id)
);

CREATE INDEX idx_kontrollsamtale_notat_vedlegg_kontrollsamtale_notat_id ON kontrollsamtale_notat_vedlegg (kontrollsamtale_notat_id);