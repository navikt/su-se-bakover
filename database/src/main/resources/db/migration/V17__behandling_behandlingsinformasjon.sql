drop table if exists vilkårsvurdering;
drop type if exists  vilkår_vurdering_status;
drop type if exists  vilkår;

alter table behandling
    add column if not exists
        behandlingsinformasjon jsonb not null
        default '{
          "uførhet": null,
          "flyktning": null,
          "lovligOpphold": null,
          "fastOppholdINorge": null,
          "oppholdIUtlandet": null,
          "formue": null,
          "personligOppmøte": null,
          "sats": null
        }';

alter table behandling
    alter column behandlingsinformasjon
        drop default;
