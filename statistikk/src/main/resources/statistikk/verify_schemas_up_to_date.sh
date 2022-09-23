#!/bin/bash
# Siden github repoet ligger internt i nav må dessverre tokensa legges inn før scriptet kjøres (en per fil). Ikke sjekk inn tokensa.
BASEDIR=$(dirname "$0")
diff "${BASEDIR}/behandling_schema.json" <(curl --silent https://raw.githubusercontent.com/navikt/saksbehandlingsstatistikk-schema/master/supplerende-stonad-grensesnitt/behandling_schema.json?token=)
echo "behandling_schema.json diff: $?"
diff "${BASEDIR}/sak_schema.json" <(curl --silent https://raw.githubusercontent.com/navikt/saksbehandlingsstatistikk-schema/master/supplerende-stonad-grensesnitt/sak_schema.json?token=)
echo "behandling_schema.json diff: $?"
diff "${BASEDIR}/stonad_schema.json" <(curl --silent https://raw.githubusercontent.com/navikt/saksbehandlingsstatistikk-schema/master/supplerende-stonad-grensesnitt/stonad_schema.json?token=)
echo "behandling_schema.json diff: $?"