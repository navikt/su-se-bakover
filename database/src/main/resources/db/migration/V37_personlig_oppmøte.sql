update behandling
set behandlingsinformasjon = jsonb_set(behandlingsinformasjon, '{personligOppmøte, status}', to_jsonb('IkkeMøttPersonlig'::text))
where behandlingsinformasjon #>> '{personligOppmøte, status}' = 'IkkeMøttOpp';

update behandling
set behandlingsinformasjon = jsonb_set(behandlingsinformasjon, '{personligOppmøte, status}', to_jsonb('IkkeMøttMenVerge'::text))
where behandlingsinformasjon #>> '{personligOppmøte, status}' = 'Verge';

update behandling
set behandlingsinformasjon = jsonb_set(behandlingsinformasjon, '{personligOppmøte, status}', to_jsonb('IkkeMøttMenSykMedLegeerklæringOgFullmakt'::text))
where behandlingsinformasjon #>> '{personligOppmøte, status}' = 'FullmektigMedLegeattest';

update behandling
set behandlingsinformasjon = jsonb_set(behandlingsinformasjon, '{personligOppmøte, status}', to_jsonb('IkkeMøttPersonlig'::text))
where behandlingsinformasjon #>> '{personligOppmøte, status}' = 'FullmektigUtenLegeattest';