UPDATE klage SET versjon = 1
WHERE type IN ('iverksatt_avvist', 'avvist', 'avsluttet')
AND fremsattrettsligklageinteresse is NULL AND opprettet < '2025-10-24';