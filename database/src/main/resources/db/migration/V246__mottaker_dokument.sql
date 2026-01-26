CREATE TABLE mottaker (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  navn text NOT NULL,
  foedselsnummer text,
  orgnummer text,
  adresse jsonb NOT NULL,
  dokument_id uuid NOT NULL UNIQUE,
  CONSTRAINT fk_mottaker_dokument FOREIGN KEY (dokument_id) REFERENCES dokument(id) ON DELETE CASCADE
);
-- constrainten her representerer det faktumet at vi kun kan ha en dokument_distribusjon per dokument hvis vi da vil ha flere brev
-- per vedtak feks må vi opprette flere innslag i dokument tabellen så vi kan ha flere distrubsjoner hver med sin unike mottaker