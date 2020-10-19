update søknad set søknadinnhold = søknadinnhold #- '{boforhold, ektemakeEllerSamboerUnder67År}';
update søknad set søknadinnhold = søknadinnhold #- '{boforhold, ektemakeEllerSamboerUførFlyktning}';

update søknad set søknadinnhold = jsonb_set(søknadinnhold, '{boforhold,delerBolig}', 'false') where søknadinnhold #>> '{boforhold, delerBoligMed}' = 'EKTEMAKE_SAMBOER';
update søknad set søknadinnhold = jsonb_set(søknadinnhold, '{boforhold,delerBoligMed}', 'null') where søknadinnhold #>> '{boforhold, delerBoligMed}' = 'EKTEMAKE_SAMBOER';
