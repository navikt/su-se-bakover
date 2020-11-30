update behandling
set behandlingsinformasjon = jsonb_set(behandlingsinformasjon #- '{formue,ektefellesVerdier}',
                 '{formue, epsVerdier}',
                 behandlingsinformasjon #> '{formue, ektefellesVerdier}')
WHERE behandlingsinformasjon #> '{formue, ektefellesVerdier}' is not null;