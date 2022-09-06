-- migrerer brevvalg-verdier i avsluttet revurdering.
update revurdering
set avsluttet = jsonb_set(
        avsluttet,
        '{brevvalg,type}',
        '"SAKSBEHANDLER_VALG_SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST"',
        false
    )
where avsluttet is not null
  and avsluttet -> 'brevvalg' ->> 'type' = 'SAKSBEHANDLER_VALG_SKAL_SENDE_BREV_MED_FRITEKST';

-- migrerer brevvalg-verdier i avsluttet revurdering.
update revurdering
set avsluttet = jsonb_set(avsluttet, '{brevvalg,type}', '"SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST"', false)
where avsluttet is not null
  and avsluttet -> 'brevvalg' ->> 'type' = 'SKAL_SENDE_BREV_MED_FRITEKST';

-- legger til brevvalg->type for avvist søknad med informasjonsbrev (det finnes 1 tilfelle av dette i prod per 13 sep 2022)
update søknad s
set lukket = jsonb_set(
        lukket, '{brevvalg}',
        '{
          "type": "SAKSBEHANDLER_VALG_SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST"
        }'
    )
where lukket is not null
  and lukket ->> 'type' = 'AVVIST'
  and exists(select 1 from dokument where søknadid = s.id and type = 'INFORMASJON_ANNET' and tittel = 'Søknaden din om supplerende stønad er avvist');

-- legger til brevvalg->fritekst for avvist søknad med informasjonsbrev (det finnes ett tilfelle av dette i prod per 13 sep 2022)
update søknad s
set lukket = jsonb_insert(
        lukket,
        '{brevvalg, fritekst}',
        to_jsonb(
                (select generertdokumentjson ->> 'fritekst'
                 from dokument
                 where søknadid = s.id
                   and type = 'INFORMASJON_ANNET'
                   and tittel = 'Søknaden din om supplerende stønad er avvist')
            ),
        true
    )
where lukket is not null
  and lukket ->> 'type' = 'AVVIST'
  and exists(select 1 from dokument where søknadid = s.id and type = 'INFORMASJON_ANNET' and tittel = 'Søknaden din om supplerende stønad er avvist');

-- legger til brevvalg->type for avvist søknad med vedtaksbrev (det finnes ingen tilfeller av dette i prod per 13 sep 2022)
update søknad s
set lukket = jsonb_set(lukket, '{brevvalg}', '{
  "type": "SAKSBEHANDLER_VALG_SKAL_SENDE_VEDTAKSBREV_UTEN_FRITEKST"
}')
where lukket is not null
  and lukket ->> 'type' = 'AVVIST'
  and exists(select 1 from dokument where søknadid = s.id and type = 'VEDTAK' and tittel = 'Søknaden din om supplerende stønad er avvist');

-- legger til brevvalg->fritekst for avvist søknad med vedtaksbrev (det finnes ingen tilfeller av dette i prod per 13 sep 2022)
update søknad s
set lukket = jsonb_insert(
        lukket,
        '{brevvalg, fritekst}',
        coalesce(to_jsonb(
                         (select generertdokumentjson ->> 'fritekst'
                          from dokument
                          where søknadid = s.id
                            and type = 'VEDTAK'
                            and tittel = 'Søknaden din om supplerende stønad er avvist')
                     ), 'null'),
        true
    )
where lukket is not null
  and lukket ->> 'type' = 'AVVIST'
  and exists(select 1 from dokument where søknadid = s.id and type = 'VEDTAK' and tittel = 'Søknaden din om supplerende stønad er avvist');

-- legger til brevvalg->type for avvist søknad uten brev (det finnes 9 tilfeller av dette i prod per 13 sep 2022)
update søknad s
set lukket = jsonb_set(lukket, '{brevvalg}', '{
  "type": "SAKSBEHANDLER_VALG_SKAL_IKKE_SENDE_BREV"
}')
where lukket is not null
  and lukket ->> 'type' = 'AVVIST'
  and not exists((select 1 from dokument where søknadid = s.id));

-- legger til brevvalg->fritekst 'null' for avvist søknad uten brev
update søknad s
set lukket = jsonb_insert(
        lukket,
        '{brevvalg, fritekst}',
        'null',
        true
    )
where lukket is not null
  and lukket ->> 'type' = 'AVVIST'
  and not exists((select 1 from dokument where søknadid = s.id and ((type = 'VEDTAK' and tittel = 'Søknaden din om supplerende stønad er avvist') or (type = 'INFORMASJON_ANNET' and tittel = 'Søknaden din om supplerende stønad er avvist'))));

-- legger til brevvalg->type for bortfalt søknad (det finnes 19 tilfeller av dette i prod per 13 sep 2022)
update søknad
set lukket = jsonb_set(lukket, '{brevvalg}', '{
  "type": "SKAL_IKKE_SENDE_BREV"
}')
where lukket is not null
  and lukket ->> 'type' = 'BORTFALT';

-- legger til brevvalg->fritekst for bortfalt søknad (det har ikke vært sendt brev i disse tilfellene)
update søknad s
set lukket = jsonb_insert(
        lukket,
        '{brevvalg, fritekst}',
        'null',
        true
    )
where lukket is not null
  and lukket ->> 'type' = 'BORTFALT';

-- legger til brevvalg->type for trukket søknad
update søknad
set lukket = jsonb_set(lukket, '{brevvalg}', '{
  "type": "SKAL_SENDE_INFORMASJONSBREV_UTEN_FRITEKST"
}')
where lukket is not null
  and lukket ->> 'type' = 'TRUKKET';

-- legger til brevvalg->trukketDato for trukket søknad
update søknad s
set lukket = jsonb_insert(
        lukket,
        '{brevvalg, trukketDato}',
        coalesce(to_jsonb(
                         (select generertdokumentjson ->> 'trukketDato'
                          from dokument
                          where søknadid = s.id
                            and type = 'INFORMASJON_ANNET'
                            and tittel = 'Bekrefter at søknad er trukket')
                     ), 'null'),
        true
    )
where lukket is not null
  and lukket ->> 'type' = 'TRUKKET'
  and exists(select 1 from dokument where søknadid = s.id and type = 'INFORMASJON_ANNET' and tittel = 'Bekrefter at søknad er trukket');

-- legger til brevvalg->fritekst 'null' for trukket søknad
update søknad s
set lukket = jsonb_insert(
        lukket,
        '{brevvalg, fritekst}',
        'null',
        true
    )
where lukket is not null
  and lukket ->> 'type' = 'TRUKKET';

-- legger til brevvalg->begrunnelse for alle lukkede søknader som null siden vi ikke har tatt inn en begrunnelse her.
update søknad
set lukket = jsonb_insert(lukket, '{brevvalg, begrunnelse}', 'null', true)
where lukket is not null;

-- Testdata: http://sqlfiddle.com/#!17/ae522e/12
-- create table revurdering(id int primary key, avsluttet jsonb);
-- insert into revurdering (id,avsluttet) values (1,'{"begrunnelse":"b1","fritekst":"f1","brevvalg":{"type":"SKAL_SENDE_BREV_MED_FRITEKST","fritekst":"f1","begrunnelse":"b1"}}');
-- insert into revurdering (id,avsluttet) values (2,'{"begrunnelse":"b2","fritekst":null,"brevvalg":{"type":"SKAL_IKKE_SENDE_BREV","fritekst":null,"begrunnelse":"b2"}}');
-- insert into revurdering (id,avsluttet) values (3,null);
--
-- create table søknad(id int primary key, lukket jsonb);
-- create table dokument(id int primary key, type text, tittel text, søknadid int references søknad(id), generertdokumentjson jsonb);
-- insert into søknad (id,lukket) values (1,'{"type":"BORTFALT"}'); -- ikke knyttet til dokument
-- insert into søknad (id,lukket) values (2,'{"type":"TRUKKET"}'); -- knyttes alltid til et informasjonsdokument
-- insert into dokument (id,søknadid,type,tittel,generertdokumentjson) values (1,2,'INFORMASJON_ANNET','Bekrefter at søknad er trukket','{"trukketDato":"2021-01-01"}');
--
-- insert into søknad (id,lukket) values (3,'{"type":"AVVIST"}'); -- avvist uten brev (ikke knyttet til dokument)
--
-- insert into søknad (id,lukket) values (4,'{"type":"AVVIST"}'); --avvist med informasjonsbrev med fritekst
-- insert into dokument (id,søknadid,type,tittel,generertdokumentjson) values (2,4,'INFORMASJON_ANNET','Søknaden din om supplerende stønad er avvist', '{"fritekst":"avvist med informasjonsbrev med fritekst"}');
--
-- insert into søknad (id,lukket) values (5,'{"type":"AVVIST"}'); --avvist med vedtaksbrev med fritekst;
-- insert into dokument (id,søknadid,type,tittel,generertdokumentjson) values (3,5,'VEDTAK','Søknaden din om supplerende stønad er avvist', '{"fritekst":"avvist med vedtaksbrev med fritekst"}');
--
-- insert into søknad (id,lukket) values (6,'{"type":"AVVIST"}');  --avvist med vedtaksbrev uten fritekst;
-- insert into dokument (id,søknadid,type,tittel,generertdokumentjson) values (4,6,'VEDTAK','Søknaden din om supplerende stønad er avvist', '{"fritekst":null}');
-- select s.id as søknadId, s.lukket søknadLukket, d.id as dokumentId, d.type as dokumentType, d.tittel as dokumentTittel, d.generertdokumentjson as dokumentJson from søknad s left join dokument d on s.id = d.søknadid order by s.id;
