package no.nav.su.se.bakover.service.statistikk.mappers

import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.service.statistikk.Statistikk
import java.time.Clock

class SakStatistikkMapper(
    private val clock: Clock,
) {
    fun map(sak: Sak, aktørId: AktørId): Statistikk.Sak {
        return Statistikk.Sak(
            funksjonellTid = sak.opprettet,
            tekniskTid = sak.opprettet,
            opprettetDato = sak.opprettet.toLocalDate(zoneIdOslo),
            sakId = sak.id,
            aktorId = aktørId.toString().toLong(),
            saksnummer = sak.saksnummer.nummer,
            sakStatus = "OPPRETTET",
            sakStatusBeskrivelse = "Sak er opprettet men ingen vedtak er fattet.",
            versjon = clock.millis(),
        )
    }
}
