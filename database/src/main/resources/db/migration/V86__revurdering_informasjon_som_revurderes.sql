alter table revurdering
    add COLUMN informasjonSomRevurderes jsonb;

update revurdering set informasjonsomrevurderes =
    case
        when revurderingstype = 'OPPRETTET' and årsak = 'REGULER_GRUNNBELØP' then '{"Inntekt":"IkkeVurdert","Uførhet":"IkkeVurdert"}'::json
        when revurderingstype = 'OPPRETTET' and årsak != 'REGULER_GRUNNBELØP' then '{"Inntekt":"IkkeVurdert"}'::json
        when revurderingstype != 'OPPRETTET' and årsak = 'REGULER_GRUNNBELØP' then '{"Inntekt":"Vurdert", "Uførhet":"Vurdert"}'::json
        when revurderingstype != 'OPPRETTET' and årsak != 'REGULER_GRUNNBELØP' then '{"Inntekt":"Vurdert"}'::json
    end;

alter table revurdering
    alter COLUMN informasjonSomRevurderes SET NOT NULL;