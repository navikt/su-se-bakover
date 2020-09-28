package no.nav.su.se.bakover.database.avstemming

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import java.time.Instant

interface AvstemmingRepo {
    fun opprettAvstemming(avstemming: Avstemming): Avstemming
    fun hentAvstemming(id: UUID30): Avstemming?
    fun oppdaterAvstemteUtbetalinger(avstemming: Avstemming)
    fun hentSisteAvstemming(): Avstemming?
    fun hentUtbetalingerForAvstemming(fom: Instant, tom: Instant): List<Utbetaling>
}
