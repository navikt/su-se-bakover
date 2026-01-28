CREATE TABLE mottaker (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  navn text NOT NULL,
  foedselsnummer text,
  orgnummer text,
  adresse jsonb NOT NULL,
  dokument_id uuid, -- må settes når dokumentet genereres TODO må vurderes om det er nødvendig eller om det skal ligge på dokumentet og peke hit
  sakId uuid NOT NULL,
  referanse_type text NOT NULL,
  referanse_id uuid NOT NULL,
  CONSTRAINT fk_mottaker_sak
      FOREIGN KEY (sakid) REFERENCES sak(id),
  CONSTRAINT referanse_type_requires_id
      CHECK (
          referanse_type IS NULL
              OR referanse_id IS NOT NULL
          )
);
-- constrainten her representerer det faktumet at vi kun kan ha en dokument_distribusjon per dokument hvis vi da vil ha flere brev
-- per vedtak feks må vi opprette flere innslag i dokument tabellen så vi kan ha flere distrubsjoner hver med sin unike mottaker