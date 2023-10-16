package infrastructure

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelser
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelser

class TilbakekrevingsbehandlingPostgresRepoTest {

    @Test
    fun `henter alle tilbakekrevingsbehandlingHendelser & tilhørende hendelser for sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)

            val opprettet = testDataHelper.persisterOpprettetTilbakekrevingsbehandlingHendelse()
            val oppgaveHendelse = testDataHelper.persisterOppgaveHendelseFraRelatertHendelse { opprettet }

            testDataHelper.tilbakekrevingHendelseRepo.hentForSak(opprettet.sakId) shouldBe TilbakekrevingsbehandlingHendelser.create(
                sakId = opprettet.sakId,
                clock = fixedClock,
                hendelser = listOf(opprettet),
                kravgrunnlagPåSak = KravgrunnlagPåSakHendelser(emptyList()),
                oppgaveHendelser = listOf(oppgaveHendelse),
                dokumentHendelser = listOf(),
            )
        }
    }
}
