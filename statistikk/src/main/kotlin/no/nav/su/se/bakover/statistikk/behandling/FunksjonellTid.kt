package no.nav.su.se.bakover.statistikk.behandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

private val log = LoggerFactory.getLogger("Attesteringshistorikk.toFunksjonellTid")

/**
 * Logger error dersom vi ikke finner siste attesteringstidspunkt.
 * @param id kun for logging
 * @param clock bruker Tidspunkt.now(clock) dersom vi ikke finner siste attesteringstidspunkt.
 * @return Siste attesterings tidspunkt eller nå dersom det ikke finnes.
 */
fun Attesteringshistorikk.toFunksjonellTid(
    id: UUID,
    clock: Clock,
): Tidspunkt {
    return this.prøvHentSisteAttestering()?.opprettet ?: Tidspunkt.now(clock).also {
        log.error("Kunne ikke avgjøre funksjonellTid da det ikke fantes noen attesteringer (defaulter til Tidspunkt.now(clock)) ved statistikkhendelse med behandlingId $id - kunne ikke hente siste attestering")
    }
}
