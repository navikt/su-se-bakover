-- Slettes fordi det ble opprettet dobbelt opp manuelt gjennom driftside
delete from stoenad_maaned_statistikk where maaned = '2025-01-01' and teknisk_tid < '2026-01-21 13:46:00';
