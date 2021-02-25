update behandling set behandlingsinformasjon = behandlingsinformasjon #- '{bosituasjon, epsAlder}';
update behandling set behandlingsinformasjon = behandlingsinformasjon #- '{formue, borSøkerMedEPS}';

update behandling set behandlingsinformasjon = jsonb_set(behandlingsinformasjon, '{bosituasjon, ektemakeEllerSamboerUførFlyktning}', 'null')
where behandlingsinformasjon #>> '{ektefelle}' is null or behandlingsinformasjon #>> '{ektefelle}' = '{ "type": "IngenEktefelle" }';

