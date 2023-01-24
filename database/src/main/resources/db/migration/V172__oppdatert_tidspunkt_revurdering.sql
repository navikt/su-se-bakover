alter table
    revurdering
add column
    if not exists
        oppdatert timestamp with time zone
        default null;

comment on column revurdering.oppdatert is 'Oppdatert Tidspunktet referer til når selve revurderingen ble oppdatert. For eksempel ved endring av periode, årsak, hva som revurderes, eller begrunnelse'