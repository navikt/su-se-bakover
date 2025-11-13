CREATE TABLE fritekst
(
    referanse_id UUID NOT NULL,
    type         TEXT NOT NULL,
    fritekst     TEXT NOT NULL
);
ALTER TABLE fritekst
    ADD CONSTRAINT fritekst_unique_ref_type UNIQUE (referanse_id, type);