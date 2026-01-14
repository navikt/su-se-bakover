-- Tidlige linjer populerte feilaktig funksjonell_tid med opprettet dato til behandling
-- Siden teknisk tid i vår løsning tilsvarere funksjonell tid (populeres noen ms etter) kan det brukes som erstatning.
update sak_statistikk set funksjonell_tid = teknisk_tid
where funksjonell_tid::date != teknisk_tid::date;
