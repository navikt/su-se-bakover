package no.nav.su.se.bakover.database.person

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import java.util.UUID

interface PersonRepo {
    fun hentFnrForSak(sakId: UUID): Fnr?
    fun hentFnrForSøknad(søknadId: UUID): Fnr?
    fun hentFnrForBehandling(behandlingId: UUID): Fnr?
    fun hentFnrForUtbetaling(utbetalingId: UUID30): Fnr?
}
