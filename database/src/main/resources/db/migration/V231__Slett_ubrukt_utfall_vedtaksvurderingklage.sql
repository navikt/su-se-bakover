UPDATE klage
SET vedtaksvurdering = vedtaksvurdering - 'utfall'
WHERE vedtaksvurdering::text LIKE '%TIL_GUNST%'
   OR vedtaksvurdering::text LIKE '%TIL_UGUNST%';