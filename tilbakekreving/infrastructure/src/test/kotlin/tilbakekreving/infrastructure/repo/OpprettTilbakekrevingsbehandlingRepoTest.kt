package tilbakekreving.infrastructure.repo

import dokument.domain.DokumentHendelser
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelser

/**
 * Disse testene dekker også hentForSak og hent(hendelseId)
 */
class OpprettTilbakekrevingsbehandlingRepoTest {

    @Test
    fun `kan opprette og hente tilbakekrevingsbehandling`() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)

            val (sak, _, _, _, _, _, hendelse, oppgaveHendelse) = testDataHelper.persisterOpprettetTilbakekrevingsbehandlingHendelse()

            val actual = testDataHelper.tilbakekrevingHendelseRepo.hentForSak(sak.id)
            val actualKravgrunnlag =
                testDataHelper.kravgrunnlagPostgresRepo.hentKravgrunnlagPåSakHendelser(sak.id).also {
                    it.size shouldBe 1
                    it.detaljerSortert.first().kravgrunnlag shouldBe sak.uteståendeKravgrunnlag
                }
            actual shouldBe TilbakekrevingsbehandlingHendelser.create(
                sakId = sak.id,
                clock = fixedClock,
                hendelser = listOf(hendelse),
                kravgrunnlagPåSak = actualKravgrunnlag,
                oppgaveHendelser = listOf(oppgaveHendelse),
                dokumentHendelser = DokumentHendelser.empty(sak.id),
            )
        }
    }
}
