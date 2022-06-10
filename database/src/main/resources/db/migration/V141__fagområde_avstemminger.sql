alter table if exists avstemming add column fagområde text not null default 'SUUFORE';
alter table if exists konsistensavstemming add column fagområde text not null default 'SUUFORE';