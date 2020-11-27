update søknad set søknadinnhold = jsonb_set(søknadinnhold, '{boforhold, ektefellePartnerSamboer, fnr}', to_jsonb('16113113816'::text))
where søknadinnhold #>> '{boforhold, ektefellePartnerSamboer, type}' = 'UtenFnr';
update søknad set søknadinnhold = søknadinnhold #- '{boforhold, ektefellePartnerSamboer, type}';
update søknad set søknadinnhold = søknadinnhold #- '{boforhold, ektefellePartnerSamboer, navn}';
update søknad set søknadinnhold = søknadinnhold #- '{boforhold, ektefellePartnerSamboer, fødselsdato}';