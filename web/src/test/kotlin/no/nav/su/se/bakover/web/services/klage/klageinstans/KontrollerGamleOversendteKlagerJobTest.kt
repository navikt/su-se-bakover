package no.nav.su.se.bakover.web.services.klage.klageinstans

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.domain.job.JobbResultat
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.tid.toTidspunkt
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.test.oversendtKlage
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.slf4j.Logger
import java.time.Clock
import java.time.Instant

internal class KontrollerGamleOversendteKlagerJobTest {

    private val nå = Instant.parse("2026-09-23T10:00:00Z")
    private val clock = Clock.fixed(nå, zoneIdOslo)

    @Test
    fun `varsler ikke når ingen oversendte klager har ventet mer enn seks måneder`() {
        val nøyaktigSeksMåneder = nå.atZone(zoneIdOslo).minusMonths(6).toInstant().toTidspunkt()
        val klage = oversendtKlage(opprettet = nøyaktigSeksMåneder).second
        val klageRepo = mock<KlageRepo> {
            on { hentOversendteKlagerUtenKlageinstanshendelser() } doReturn listOf(klage)
        }
        val log = mock<Logger>()

        KontrollerGamleOversendteKlagerJob.run(
            klageRepo = klageRepo,
            clock = clock,
            log = log,
        ).shouldBeInstanceOf<JobbResultat.Ok>()

        verifyNoInteractions(log)
    }

    @Test
    fun `varsler samlet når oversendte klager har ventet mer enn seks måneder`() {
        val eldsteTidspunkt = nå.atZone(zoneIdOslo).minusMonths(7).toInstant().toTidspunkt()
        val nyesteTidspunkt = nå.atZone(zoneIdOslo).minusMonths(6).minusSeconds(1).toInstant().toTidspunkt()
        val klageRepo = mock<KlageRepo> {
            on { hentOversendteKlagerUtenKlageinstanshendelser() } doReturn listOf(
                oversendtKlage(opprettet = nyesteTidspunkt).second,
                oversendtKlage(opprettet = eldsteTidspunkt).second,
            )
        }
        val log = mock<Logger>()
        val forventetMelding =
            "2 oversendte klager har ventet mer enn seks måneder på svar fra Klageinstansen. " +
                "Eldste oversendelsestidspunkt er $eldsteTidspunkt."

        val resultat = KontrollerGamleOversendteKlagerJob.run(
            klageRepo = klageRepo,
            clock = clock,
            log = log,
        ).shouldBeInstanceOf<JobbResultat.DelvisFeilet>()

        resultat.melding shouldBe forventetMelding
        verify(log).error(forventetMelding)
    }
}
