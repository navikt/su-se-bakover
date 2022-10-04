package no.nav.su.se.bakover.domain.person

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.UUID30
import java.util.UUID

/**
 * Metodene her returnerer en liste fordi de kan inneholde fnr for både søker og søkers EPS
 */
interface PersonRepo {
    fun hentFnrForSak(sakId: UUID): List<Fnr>
    fun hentFnrForSøknad(søknadId: UUID): List<Fnr>
    fun hentFnrForBehandling(behandlingId: UUID): List<Fnr>
    fun hentFnrForUtbetaling(utbetalingId: UUID30): List<Fnr>
    fun hentFnrForRevurdering(revurderingId: UUID): List<Fnr>
    fun hentFnrForVedtak(vedtakId: UUID): List<Fnr>
    fun hentFnrForKlage(klageId: UUID): List<Fnr>
}
