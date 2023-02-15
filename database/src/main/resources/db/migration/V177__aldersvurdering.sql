ALTER TABLE
    behandling
    ADD COLUMN
        IF NOT EXISTS
        aldersvurdering JSONB DEFAULT NULL;

/*
 Oppdaterer først alle behandlingene til å være historiske med angitt data.
 Så oppdaterer spesifikke behandlinger som er avslag pga manglende dokumentasjon med riktig vurdering

 eller bare set inn where clause?

 */
UPDATE
    behandling b
SET aldersvurdering = '{
  "vurdering": "HISTORISK",
  "fødselsdato": null,
  "fødselsår": null,
  "alder": null,
  "alderSøkerFyllerIÅr": null,
  "alderPåTidspunkt": null,
  "overstyrtAvSaksbehandler": false
}';


UPDATE
    behandling b
SET aldersvurdering = '{
  "vurdering": "SKAL_IKKE_VURDERES",
  "fødselsdato": null,
  "fødselsår": null,
  "alder": null,
  "alderSøkerFyllerIÅr": null,
  "alderPåTidspunkt": null,
  "overstyrtAvSaksbehandler": false
}'
from vilkårsvurdering_opplysningsplikt v
where b.id = v.behandlingid
  And v.resultat = 'AVSLAG'



