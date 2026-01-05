update sak_statistikk set behandling_status = 'REGISTRERT' where behandling_status = 'Registrert';
update sak_statistikk set behandling_status = 'IVERKSATT' where behandling_status = 'Iverksatt';
update sak_statistikk set behandling_status = 'TIL_ATTESTERING' where behandling_status = 'TilAttestering';
update sak_statistikk set behandling_status = 'AVSLUTTET' where behandling_status = 'Avsluttet';
update sak_statistikk set behandling_status = 'UNDERKJENT' where behandling_status = 'Underkjent';
update sak_statistikk set behandling_status = 'OVERSENDT' where behandling_status = 'OversendtKlage';

update sak_statistikk set behandling_resultat = 'STANSET' where behandling_resultat = 'Stanset';
update sak_statistikk set behandling_resultat = 'GJENOPPTATT' where behandling_resultat = 'Gjenopptatt';
update sak_statistikk set behandling_resultat = 'INNVILGET' where behandling_resultat = 'Innvilget';
update sak_statistikk set behandling_resultat = 'AVVIST' where behandling_resultat = 'Avvist';
update sak_statistikk set behandling_resultat = 'AVBRUTT' where behandling_resultat = 'Avbrutt';
update sak_statistikk set behandling_resultat = 'AVSLAG' where behandling_resultat = 'Avslag';
update sak_statistikk set behandling_resultat = 'OPPHØRT' where behandling_resultat = 'Opphør';
update sak_statistikk set behandling_resultat = 'BORTFALT' where behandling_resultat = 'Bortfalt';
update sak_statistikk set behandling_resultat = 'OMGJORT' where behandling_resultat = 'OmgjortKlage';
update sak_statistikk set behandling_resultat = 'OPPRETTHOLDT' where behandling_resultat = 'OpprettholdtKlage';

UPDATE sak_statistikk SET behandling_aarsak = 'OMGJORING_ETTER_AVVIST' where behandling_aarsak = 'Omgjøring etter avvist søknad';
