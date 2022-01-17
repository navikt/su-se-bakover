alter table klagevedtak rename to klageinstansvedtak;
alter table klage add constraint unique_journalpostid unique (journalpostid);