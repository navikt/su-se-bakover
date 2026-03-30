package person.domain

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import java.util.UUID

/**
 * Metodene her returnerer en liste fordi de kan inneholde fnr for både søker og søkers EPS
 */

data class PersonerOgSakstype(
    val sakstype: Sakstype,
    val fnr: List<Fnr>,
)

interface PersonRepo {
    /** Henter fødselsnumrene knyttet til saken. Dette inkluderer alle registrerte EPS. */
    fun hentFnrOgSaktypeForSak(sakId: UUID): PersonerOgSakstype
    fun hentFnrForSøknad(søknadId: UUID): PersonerOgSakstype
    fun hentFnrForBehandling(behandlingId: UUID): PersonerOgSakstype
    fun hentFnrForUtbetaling(utbetalingId: UUID30): PersonerOgSakstype
    fun hentFnrForRevurdering(revurderingId: UUID): PersonerOgSakstype
    fun hentFnrForVedtak(vedtakId: UUID): PersonerOgSakstype
    fun hentFnrForKlage(klageId: UUID): PersonerOgSakstype
}
