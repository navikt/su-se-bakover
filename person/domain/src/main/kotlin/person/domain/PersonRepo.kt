package person.domain

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.person.Fnr
import java.util.UUID

/**
 * Metodene her returnerer en liste fordi de kan inneholde fnr for både søker og søkers EPS
 */
interface PersonRepo {
    /**
     * Henter fødselsnumrene knyttet til saken. Dette inkluderer alle registrerte EPS.
     */
    fun hentFnrForSak(sakId: UUID): List<Fnr>
    fun hentFnrForSøknad(søknadId: UUID): List<Fnr>
    fun hentFnrForBehandling(behandlingId: UUID): List<Fnr>
    fun hentFnrForUtbetaling(utbetalingId: UUID30): List<Fnr>
    fun hentFnrForRevurdering(revurderingId: UUID): List<Fnr>
    fun hentFnrForVedtak(vedtakId: UUID): List<Fnr>
    fun hentFnrForKlage(klageId: UUID): List<Fnr>
}
