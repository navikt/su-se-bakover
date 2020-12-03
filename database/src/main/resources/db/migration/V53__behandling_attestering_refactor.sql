with a as (select id, attestant from behandling),
     b as (select json_build_object('attestant',
         json_build_object( 'navIdent', a.attestant),
         'type', 'Iverksatt'::text
         )::text as value, a.id as id from a)
update behandling set attestant = b.value from b where behandling.id = b.id and behandling.attestant is not null;

alter table behandling alter column attestant type jsonb using attestant::jsonb;
alter table behandling rename column attestant to attestering;
