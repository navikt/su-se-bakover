-- Fjerner behandlingsinformasjon fra Revurdering/Vedtak i kildekoden, men beholder dataene inntil videre mtp. migreringer etc.
alter table revurdering alter column behandlingsinformasjon drop not null;
alter table vedtak alter column behandlingsinformasjon drop not null;