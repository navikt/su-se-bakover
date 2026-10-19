update sak_statistikk set behandling_resultat = 'AVVIST'
where behandling_id IN (select id from klage where type = 'iverksatt_avvist')
  and behandling_resultat = 'AVSLAG';