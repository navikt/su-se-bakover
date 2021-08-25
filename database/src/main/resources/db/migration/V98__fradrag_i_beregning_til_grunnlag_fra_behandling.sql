with fradrag_fra_behandling (behandlingId, opprettet, fradrag) as (
    select id, opprettet, jsonb_array_elements(beregning -> 'fradrag')
    from behandling
),
     mapped_values as (select behandlingId as                                            behandlingId,
                              opprettet    as                                            opprettet,
                              (fradrag -> 'periode' ->> 'fraOgMed')::date                fraogmed,
                              (fradrag -> 'periode' ->> 'tilOgMed')::date                tilogmed,
                              fradrag -> 'tilhører'                                      tilhører,
                              fradrag -> 'fradragstype'                                  fradragstype,
                              round((fradrag ->> 'månedsbeløp')::double precision)       månedsbeløp,
                              COALESCE((fradrag ->> 'utenlandskInntekt')::jsonb, 'null') utenlandskinntekt
                       from fradrag_fra_behandling
                       where fradrag ->> 'fradragstype' not in
                             ('ForventetInntekt', 'BeregnetFradragEPS', 'UnderMinstenivå')
     )

insert
into grunnlag_fradrag(id, opprettet, behandlingid, fraogmed, tilogmed, fradragstype, månedsbeløp, utenlandskinntekt,
                      tilhører)
    (select uuid_generate_v4(),
            opprettet,
            behandlingId,
            fraogmed,
            tilogmed,
            fradragstype,
            månedsbeløp,
            utenlandskinntekt,
            tilhører
     from mapped_values
    )