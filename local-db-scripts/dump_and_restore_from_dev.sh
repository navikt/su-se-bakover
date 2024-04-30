#!/bin/sh
echo "skriv inn username fra vault (admin)"
read -r username
echo "skiv inn passord fra vault (admin)"
read -r passord
echo "Det vil sannsynligvis bli logget en del pg_restore errors - disse kan ignoreres"
export PGPASSWORD="$passord"
pg_dump -U "$username" -h b27dbvl030.preprod.local -p 5432 -F c supstonad-db-15-dev > data.dump

# user er username, og pwd er passord for den lokale databasen
export PGPASSWORD="pwd"
pg_restore -c --if-exists -h localhost -p 5432 -U user -d supstonad-db-local data.dump

unset PGPASSWORD

rm -r data.dump