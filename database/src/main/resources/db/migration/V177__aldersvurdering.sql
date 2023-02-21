ALTER TABLE
    behandling
    ADD COLUMN
        IF NOT EXISTS
        aldersvurdering JSONB DEFAULT NULL;

UPDATE
    behandling b
SET aldersvurdering = '{
  "vurdering": "HISTORISK",
  "fødselsdato": null,
  "fødselsår": null,
  "alder": null,
  "alderSøkerFyllerIÅr": null,
  "alderPåTidspunkt": null,
  "saksbehandlerTattEnAvgjørelse": false,
  "avgjørelsesTidspunkt": null
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
  "saksbehandlerTattEnAvgjørelse": false,
  "avgjørelsesTidspunkt": null
}'
from vilkårsvurdering_opplysningsplikt v
where b.id = v.behandlingid
  And v.resultat = 'AVSLAG'



