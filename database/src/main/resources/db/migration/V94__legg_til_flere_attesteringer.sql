update behandling
set attestering = case
    /* Lag tom array hvis det ikke finnes attestering */
                      when ( behandling.attestering is null ) then jsonb_build_array()
    /* Annars, wrap nåværande attestering i en array og legg till opprettet med tidspunkt fra vedtak hvis det finnes. Annars default till opprettet fra behandling */
                      else jsonb_build_array(
                                  behandling.attestering || jsonb_build_object('opprettet', coalesce(v.opprettet, behandling.opprettet))
                          )
    end
from ( behandling as b left join behandling_vedtak bv on b.id = bv.søknadsbehandlingid left join vedtak v on bv.vedtakid = v.id )
where b.id = behandling.id;