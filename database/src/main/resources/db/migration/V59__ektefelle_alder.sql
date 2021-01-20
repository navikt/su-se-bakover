update behandling set behandlingsinformasjon = behandlingsinformasjon #- '{bosituasjon, epsFnr}';
update behandling set behandlingsinformasjon = jsonb_set(behandlingsinformasjon, '{bosituasjon, epsAlder}', 'null');
update behandling set behandlingsinformasjon = jsonb_set(behandlingsinformasjon, '{bosituasjon, delerBolig}', 'false')
where behandlingsinformasjon #>> '{bosituasjon, delerBolig}' is null;

update behandling set behandlingsinformasjon = behandlingsinformasjon #- '{ektefelle}';
update behandling set behandlingsinformasjon = behandlingsinformasjon || '{"ektefelle": { "type": "IngenEktefelle" }}'
where behandlingsinformasjon #>> '{ektefelle}' is not null;

update behandling set behandlingsinformasjon = jsonb_set(behandlingsinformasjon, '{formue, borSøkerMedEPS}', 'false')
where behandlingsinformasjon #>> '{formue, borSøkerMedEPS}' = 'true';
