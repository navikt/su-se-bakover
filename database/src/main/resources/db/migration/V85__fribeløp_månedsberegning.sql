-- Legg till fribeløp i månedsberegninger, i behandling
with beregnings_info as (select beregning ->> 'fradragStrategyName' strategy,
                                beregning,
                                id
                         from behandling
                         where beregning is not null),

     mapped_fribeløpsverdier as (
         select case
                    when (strategy = 'Enslig') then 0.0
                    when (strategy = 'EpsOver67År') then 14810.333333333333333
                    when (strategy = 'EpsUnder67ÅrOgUførFlyktning') then 19256.69
                    when (strategy = 'EpsUnder67År') then 0.0
                    end ny_fribeløp_eps,
                beregning,
                id
         from beregnings_info),

     ny_månedsberegninger (id, månedsberegning) as (select id,
                                                           jsonb_set(
                                                                   jsonb_array_elements(
                                                                               mapped_fribeløpsverdier.beregning -> 'månedsberegninger'),
                                                                   '{fribeløpForEps}',
                                                                   to_jsonb(ny_fribeløp_eps)
                                                               )
                                                    from mapped_fribeløpsverdier)



        , gruppert_månedsberegninger (id, månedsberegninger_json) as (select id, jsonb_agg(månedsberegning) ny_månedsberegning from ny_månedsberegninger group by id)

update behandling b
set beregning = (select jsonb_set(b.beregning, '{månedsberegninger}', to_jsonb(månedsberegninger_json))
                 from gruppert_månedsberegninger
                 where b.id = gruppert_månedsberegninger.id);

-- Legg till fribeløp i månedsberegninger, i revurdering

with beregnings_info as (select beregning ->> 'fradragStrategyName' strategy,
                                beregning,
                                id
                         from revurdering
                         where beregning is not null),

     mapped_fribeløpsverdier as (
         select case
                    when (strategy = 'Enslig') then 0.0
                    when (strategy = 'EpsOver67År') then 14810.333333333333333
                    when (strategy = 'EpsUnder67ÅrOgUførFlyktning') then 19256.69
                    when (strategy = 'EpsUnder67År') then 0.0
                    end ny_fribeløp_eps,
                beregning,
                id
         from beregnings_info),

     ny_månedsberegninger (id, månedsberegning) as (select id,
                                                           jsonb_set(
                                                                   jsonb_array_elements(
                                                                               mapped_fribeløpsverdier.beregning -> 'månedsberegninger'),
                                                                   '{fribeløpForEps}',
                                                                   to_jsonb(ny_fribeløp_eps)
                                                               )
                                                    from mapped_fribeløpsverdier)



        , gruppert_månedsberegninger (id, månedsberegninger_json) as (select id, jsonb_agg(månedsberegning) ny_månedsberegning from ny_månedsberegninger group by id)

update revurdering r
set beregning = (select jsonb_set(r.beregning, '{månedsberegninger}', to_jsonb(månedsberegninger_json))
                 from gruppert_månedsberegninger
                 where r.id = gruppert_månedsberegninger.id);

-- Legg till fribeløp i månedsberegninger, i vedtak

with beregnings_info as (select beregning ->> 'fradragStrategyName' strategy,
                                beregning,
                                id
                         from vedtak
                         where beregning is not null),

     mapped_fribeløpsverdier as (
         select case
                    when (strategy = 'Enslig') then 0.0
                    when (strategy = 'EpsOver67År') then 14810.333333333333333
                    when (strategy = 'EpsUnder67ÅrOgUførFlyktning') then 19256.69
                    when (strategy = 'EpsUnder67År') then 0.0
                    end ny_fribeløp_eps,
                beregning,
                id
         from beregnings_info),

     ny_månedsberegninger (id, månedsberegning) as (select id,
                                                           jsonb_set(
                                                                   jsonb_array_elements(
                                                                               mapped_fribeløpsverdier.beregning -> 'månedsberegninger'),
                                                                   '{fribeløpForEps}',
                                                                   to_jsonb(ny_fribeløp_eps)
                                                               )
                                                    from mapped_fribeløpsverdier)



        , gruppert_månedsberegninger (id, månedsberegninger_json) as (select id, jsonb_agg(månedsberegning) ny_månedsberegning from ny_månedsberegninger group by id)

update vedtak v
set beregning = (select jsonb_set(v.beregning, '{månedsberegninger}', to_jsonb(månedsberegninger_json))
                 from gruppert_månedsberegninger
                 where v.id = gruppert_månedsberegninger.id);