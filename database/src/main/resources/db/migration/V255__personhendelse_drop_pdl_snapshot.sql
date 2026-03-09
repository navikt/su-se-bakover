ALTER TABLE personhendelse
    DROP COLUMN IF EXISTS pdl_snapshot;

update personhendelse
set pdl_diff = pdl_diff - 'pdlTreffAdresse'
where pdl_diff is not null
  and jsonb_typeof(pdl_diff) = 'object'
  and pdl_diff ? 'pdlTreffAdresse';
