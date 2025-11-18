ALTER TABLE klage ADD COLUMN versjon INT NOT NULL DEFAULT 2;

update klage set versjon = 1 where type in
                                   ('vilkårsvurdert_bekreftet_til_vurdering',
                                    'vilkårsvurdert_utfylt_til_vurdering', 'vilkårsvurdert_utfylt_avvist', 'vilkårsvurdert_bekreftet_avvist', 'vurdert_påbegynt', 'vurdert_utfylt',
                                   'vurdert_bekreftet', 'til_attestering_til_vurdering', 'til_attestering_avvist', 'oversendt', 'omgjort')
AND fremsattrettsligklageinteresse is NULL;

UPDATE klage
SET versjon = 1
WHERE opprettet < '2025-10-24' AND fremsattrettsligklageinteresse is NULL;
-- Da fremsattrettsligklageinteresse ble innført + 1 dag. Kan selvfølgelig være saksbehandlere som ikke refresha siden
-- og lagret ned med gammel versjon men da får vi bare sette den versjonen til 1.