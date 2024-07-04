SELECT string_agg(distinct gb.eps_fnr::text, ', ')
FROM behandling b
         JOIN grunnlag_bosituasjon gb ON gb.behandlingid = b.id,
     jsonb_array_elements(b.beregning->'fradrag') AS fradrag_element
WHERE fradrag_element->>'tilhører' = 'EPS'
  AND fradrag_element->>'fradragstype' = 'Alderspensjon';