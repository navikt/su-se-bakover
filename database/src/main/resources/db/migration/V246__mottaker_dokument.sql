CREATE TABLE mottaker (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  navn text NOT NULL,
  foedselsnummer text,
  adresse jsonb NOT NULL,
  dokument_id uuid, -- må settes når dokumentet genereres TODO må vurderes om det er nødvendig eller om det skal ligge på dokumentet og peke hit
  sakId uuid NOT NULL,
  referanse_type text NOT NULL,
  referanse_id uuid NOT NULL,
  CONSTRAINT fk_mottaker_sak
      FOREIGN KEY (sakid) REFERENCES sak(id)
);

CREATE INDEX idx_mottaker_referanse_type_id
    ON mottaker (referanse_type, referanse_id);