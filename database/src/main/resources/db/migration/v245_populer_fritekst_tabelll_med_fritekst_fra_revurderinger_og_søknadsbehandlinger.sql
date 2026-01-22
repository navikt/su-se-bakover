-- Ny script for å populere fritekst-tabellen med fritekst fra eksisterende søknadsbehandlinger og revurderinger på grunn av manglende komma.
INSERT INTO fritekst (referanse_id, type, fritekst)
SELECT behandling.id, 'VEDTAKSBREV_SØKNADSBEHANDLING',
       behandling.friteksttilbrev
FROM behandling
WHERE behandling.status IN (
                            'OPPRETTET',
                            'SIMULERT',
                            'VILKÅRSVURDERT_AVSLAG',
                            'VILKÅRSVURDERT_INNVILGET',
                            'TIL_ATTESTERING_INNVILGET',
                            'TIL_ATTESTERING_AVSLAG',
                            'BEREGNET_AVSLAG',
                            'BEREGNET_INNVILGET',
                            'UNDERKJENT_AVSLAG',
                            'UNDERKJENT_INNVILGET'
    )
  AND behandling.friteksttilbrev IS NOT NULL
  AND behandling.friteksttilbrev != ''
  AND behandling.lukket = FALSE
  AND NOT EXISTS (
    SELECT 1
    FROM fritekst
    WHERE fritekst.referanse_id = behandling.id
      AND fritekst.type = 'VEDTAKSBREV_SØKNADSBEHANDLING'
);

INSERT INTO fritekst (referanse_id, type, fritekst)
SELECT revurdering.id, 'VEDTAKSBREV_REVURDERING',
       revurdering.friteksttilbrev
FROM revurdering
WHERE revurdering.revurderingstype IN (
                                       'OPPRETTET',
                                       'SIMULERT_GJENOPPTAK',
                                       'SIMULERT_INNVILGET',
                                       'SIMULERT_OPPHØRT',
                                       'SIMULERT_STANS',
                                       'TIL_ATTESTERING_INNVILGET',
                                       'TIL_ATTESTERING_OPPHØRT',
                                       'UNDERKJENT_OPPHØRT',
                                       'UNDERKJENT_INNVILGET'
    )
  AND revurdering.friteksttilbrev IS NOT NULL
  AND revurdering.friteksttilbrev != ''
  AND NOT EXISTS (
    SELECT 1
    FROM fritekst
    WHERE fritekst.referanse_id = revurdering.id
      AND fritekst.type = 'VEDTAKSBREV_REVURDERING'
);