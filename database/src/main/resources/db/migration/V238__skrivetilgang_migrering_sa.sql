DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT FROM pg_catalog.pg_roles WHERE rolname = 'cloudsqliamserviceaccount'
        ) THEN
            CREATE ROLE cloudsqliamserviceaccount LOGIN;
        END IF;
    END
$$;
GRANT USAGE on SCHEMA public to "cloudsqliamserviceaccount";
GRANT INSERT ON ALL TABLES IN SCHEMA public TO "cloudsqliamserviceaccount";