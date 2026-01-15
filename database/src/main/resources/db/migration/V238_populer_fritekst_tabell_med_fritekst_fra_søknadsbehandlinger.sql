INSERT INTO fritekst (referanse_id, type, fritekst)
SELECT behandling.id, 'VEDTAKSBREV_SØKNADSBEHANDLING',
       behandling.friteksttilbrev
FROM behandling
WHERE behandling.status IN (
    'OPPRETTET',
    'SIMULERT',
    'VILKÅRSVURDERT_AVSLAG',
    'VILKÅRSVURDERT_INNVILGET',
    'TIL_ATTESTERING_INNVILGET'
    'TIL_ATTESTERING_AVSLAG',
    'BEREGNET_AVSLAG',
    'BEREGNET_INNVILGET'
    'UNDERKJENT_AVSLAG',
    'UNDERKJENT_INNVILGET'
)
  AND behandling.friteksttilbrev IS NOT NULL
  AND behandling.lukket = FALSE
  AND NOT EXISTS (
    SELECT 1
    FROM fritekst
    WHERE fritekst.referanse_id = behandling.id
      AND fritekst.type = 'VEDTAKSBREV_SØKNADSBEHANDLING'
);