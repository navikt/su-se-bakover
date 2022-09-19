-- I forrige migreringsscript ble trukketDato lagt på lukket->>trukketDato.
-- Men det var ikke en ISO-dato, så vi må konvertere de tilfellene.
-- Dette gjelder kun for de migrerte tilfellene.
update søknad s
set lukket = jsonb_set(
        lukket,
        '{trukketDato}',
        to_jsonb(to_date((lukket ->> 'trukketDato'), 'DD.MM.YYYY')::text),
        true
    )
where lukket is not null
  and lukket ->> 'type' = 'TRUKKET'
  and lukket ->> 'trukketDato' is not null
  and (lukket ->> 'trukketDato') ~ '^\d{2}.\d{2}.\d{4}$';

-- http://sqlfiddle.com/#!17/d43830/1
-- create table søknad(id int primary key, lukket jsonb);
-- insert into søknad (id,lukket) values (1,'{"type":"TRUKKET","trukketDato": "28.08.2022"}'); -- denne skal konverteres til iso
-- insert into søknad (id,lukket) values (2,'{"type":"TRUKKET","trukketDato": "2021-01-01"}'); -- denne skal ikke konverteres
-- select * from søknad order by id;
