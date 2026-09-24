package no.nav.su.se.bakover.web.services.klage.klageinstans

import behandling.klage.domain.KlageId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.domain.job.JobbResultat
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.tid.toTidspunkt
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.OversendtKlageUtenKlageinstanshendelse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.slf4j.Logger
import java.time.Clock
import java.time.Instant
import java.util.UUID

internal class KontrollerGamleOversendteKlagerJobTest {

    private val nå = Instant.parse("2026-09-23T10:00:00Z")
    private val clock = Clock.fixed(nå, zoneIdOslo)

    @Test
    fun `varsler ikke når ingen oversendte klager har ventet mer enn seks måneder`() {
        val nøyaktigSeksMåneder = nå.atZone(zoneIdOslo).minusMonths(6).toInstant().toTidspunkt()
        val klageRepo = mock<KlageRepo> {
            on { hentOversendteKlagerUtenKlageinstanshendelserFør(nøyaktigSeksMåneder) } doReturn emptyList()
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
        val grense = nå.atZone(zoneIdOslo).minusMonths(6).toInstant().toTidspunkt()
        val eldsteKlageId = KlageId.generer()
        val eldsteSakId = UUID.randomUUID()
        val nyesteKlageId = KlageId.generer()
        val nyesteSakId = UUID.randomUUID()
        val klageRepo = mock<KlageRepo> {
            on { hentOversendteKlagerUtenKlageinstanshendelserFør(grense) } doReturn listOf(
                OversendtKlageUtenKlageinstanshendelse(
                    klageId = eldsteKlageId,
                    sakId = eldsteSakId,
                ),
                OversendtKlageUtenKlageinstanshendelse(
                    klageId = nyesteKlageId,
                    sakId = nyesteSakId,
                ),
            )
        }
        val log = mock<Logger>()
        val forventetMelding =
            "2 oversendte klager har ventet mer enn seks måneder på svar fra Klageinstansen:\n" +
                "- klageId=${eldsteKlageId.value}, sakId=$eldsteSakId\n" +
                "- klageId=${nyesteKlageId.value}, sakId=$nyesteSakId"

        val resultat = KontrollerGamleOversendteKlagerJob.run(
            klageRepo = klageRepo,
            clock = clock,
            log = log,
        ).shouldBeInstanceOf<JobbResultat.DelvisFeilet>()

        resultat.melding shouldBe forventetMelding
        verify(log).error(forventetMelding)
    }
}
