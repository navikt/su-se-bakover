--Add tidspunkt to existing oppdragsmeldinger.
update utbetaling set oppdragsmelding = oppdragsmelding || '{"tidspunkt":"2020-09-10T00:00:00.000000000Z"}'
where oppdragsmelding is not null;

