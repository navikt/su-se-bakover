update sak_statistikk set behandling_resultat = 'TRUKKET' where behandling_resultat = 'Trukket';

update sak_statistikk set behandling_metode = 'MANUELL' where behandling_metode = 'Manuell';
update sak_statistikk set behandling_metode = 'AUTOMATISK' where behandling_metode = 'Automatisk';
