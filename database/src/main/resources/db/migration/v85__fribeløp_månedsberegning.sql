with eps_info as (select (behandlingsinformasjon -> 'ektefelle' ->> 'alder')::int                        epsÅlder,
                         behandlingsinformasjon -> 'bosituasjon' ->> 'ektemakeEllerSamboerUførFlyktning' epsErUfør,
                         beregning,
                         id
                  from behandling),

     mapped_fribeløpsverdier as (
         select case
                    when (epsÅlder is null) then null
                    when (epsÅlder >= 67) then 14810.333333333333333
                    when (epsÅlder < 67 and epsErUfør = 'true') then 19256.69
                    when (epsÅlder < 67 and epsErUfør = 'false') then 0
                    end ny_fribeløp_eps,
                beregning,
                id
         from eps_info),

     ny_månedsberegninger (id, månedsberegning) as (select id,
                                                         jsonb_set(
                                                                 jsonb_array_elements(
                                                                         mapped_fribeløpsverdier.beregning -> 'månedsberegninger'),
                                                                 '{fribeløpForEps}',
                                                                 case
                                                                     when (ny_fribeløp_eps is null) then 'null'
                                                                     else to_jsonb(ny_fribeløp_eps)
                                                                     end
                                                             )
                                                  from mapped_fribeløpsverdier)

update behandling b
set beregning = (select jsonb_set(b.beregning, '{månedsberegning}', to_jsonb(ny_månedsberegning)))
from (select id, jsonb_agg(månedsberegning) ny_månedsberegning from ny_månedsberegninger group by id) a
where b.id = a.id;

