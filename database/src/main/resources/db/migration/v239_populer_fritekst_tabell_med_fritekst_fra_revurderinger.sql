INSERT INTO fritekst (referanse_id, type, fritekst)
SELECT revurdering.id, 'VEDTAKSBREV_REVURDERING',
       revurdering.friteksttilbrev
FROM revurdering
WHERE revurdering.revurderingstype IN (
    'OPPRETTET',
    'SIMULERT_GJENOPPTAK',
    'SIMULERT_INNVILGET',
    'SIMULERT_OPPHØRT'
    'SIMULERT_STANS',
    'TIL_ATTESTERING_INNVILGET'
    'TIL_ATTESTERING_OPPHØRT',
    'UNDERKJENT_OPPHØRT',
    'UNDERKJENT_INNVILGET'
    )
  AND revurdering.friteksttilbrev IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM fritekst
    WHERE fritekst.referanse_id = revurdering.id
      AND fritekst.type = 'VEDTAKSBREV_REVURDERING'
);