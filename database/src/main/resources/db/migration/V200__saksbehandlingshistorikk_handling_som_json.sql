UPDATE behandling
SET saksbehandling = jsonb_set(
        saksbehandling,
        '{historikk}',
        (SELECT jsonb_agg(
                        jsonb_build_object(
                                'navIdent', obj ->> 'navIdent',
                                'tidspunkt', obj ->> 'tidspunkt',
                                'handlingJson', jsonb_build_object(
                                        'handling', obj ->> 'handling',
                                        'tilhørendeSøknadsbehandlingId', null
                                                )
                        )
                )
         FROM jsonb_array_elements(saksbehandling -> 'historikk') WITH ORDINALITY AS arr(obj, idx))
                     )
WHERE jsonb_array_length(saksbehandling->'historikk') > 0
  AND NOT EXISTS (
    SELECT 1
    FROM jsonb_array_elements(saksbehandling->'historikk') AS element
    WHERE element ? 'handlingJson'
);