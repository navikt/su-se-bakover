package infrastructure.repo

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
    fun `henter alle tilbakekrevingsbehandlingHendelser & tilhørende hendelser for sak`() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)

            val (sak, _, _, _, opprettetHendelse) = testDataHelper.persisterOpprettetTilbakekrevingsbehandlingHendelse()
            val oppgaveHendelse = testDataHelper.persisterOppgaveHendelseFraRelatertHendelse { opprettetHendelse }

            val actual = testDataHelper.tilbakekrevingHendelseRepo.hentForSak(opprettetHendelse.sakId)
            val actualKravgrunnlag =
                testDataHelper.kravgrunnlagPostgresRepo.hentKravgrunnlagPåSakHendelser(sak.id).also {
                    it.size shouldBe 1
                    it.first().kravgrunnlag shouldBe sak.uteståendeKravgrunnlag
                }
            actual shouldBe TilbakekrevingsbehandlingHendelser.create(
                sakId = opprettetHendelse.sakId,
                clock = fixedClock,
                hendelser = listOf(opprettetHendelse),
                kravgrunnlagPåSak = actualKravgrunnlag,
                oppgaveHendelser = listOf(oppgaveHendelse),
                dokumentHendelser = listOf(),
            )
        }
    }
}
