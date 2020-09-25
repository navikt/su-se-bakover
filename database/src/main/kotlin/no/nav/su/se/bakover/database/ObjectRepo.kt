package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.MicroInstant
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import java.util.UUID

interface ObjectRepo {
    fun hentSak(fnr: Fnr): Sak?
    fun hentSak(sakId: UUID): Sak?
    fun opprettSak(fnr: Fnr): Sak
    fun hentSøknad(søknadId: UUID): Søknad?
    fun hentBehandling(behandlingId: UUID): Behandling?
    fun hentBeregning(behandlingId: UUID): Beregning?
    fun hentUtbetaling(utbetalingId: UUID30): Utbetaling?
    fun hentUtbetalingerForAvstemming(fom: MicroInstant, tom: MicroInstant): List<Utbetaling>
    fun opprettAvstemming(avstemming: Avstemming): Avstemming
    fun hentSisteAvstemming(): Avstemming?
}
