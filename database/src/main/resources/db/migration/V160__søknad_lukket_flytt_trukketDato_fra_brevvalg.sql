-- I forrige migreringsscript ble trukketDato lagt på lukket->brevvalg->trukketDato.
-- Den skulle havnet på lukket->trukketDato.
-- Dette gjelder kun for de migrerte tilfellene.
update søknad s
set lukket = jsonb_insert(
        lukket,
        '{trukketDato}',
        to_jsonb((lukket -> 'brevvalg' ->> 'trukketDato')),
        true
    )
where lukket is not null
  and lukket ->> 'type' = 'TRUKKET'
  and lukket -> 'brevvalg' ->> 'trukketDato' is not null
  and lukket ->> 'trukketDato' is null;

-- http://sqlfiddle.com/#!17/543a4/2
-- create table søknad(id int primary key, lukket jsonb);
-- insert into søknad (id,lukket) values (1,'{"type":"TRUKKET","brevvalg":{"trukketDato":"2021-01-01"}}');
-- insert into søknad (id,lukket) values (2,'{"type":"TRUKKET","trukketDato": "2021-01-02", "brevvalg":{"trukketDato":null}}');
-- insert into søknad (id,lukket) values (3,'{"type":"TRUKKET","trukketDato": "2021-01-03", "brevvalg":{}}');
-- insert into søknad (id,lukket) values (4,'{"type":"TRUKKET","trukketDato": "2021-01-04", "brevvalg":{"trukketDato":"2021-01-04"}}');
-- select * from søknad order by id;