package tilbakekreving.infrastructure.repo

import dokument.domain.DokumentHendelser
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelser

/**
 * Disse testene dekker også hentForSak og hent(hendelseId)
 */
class OpprettTilbakekrevingsbehandlingRepoTest {
    // TODO: for testing enablet
    @Disabled
    @Test
    fun `kan opprette og hente tilbakekrevingsbehandling`() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)

            val (sak, _, _, _, _, _, hendelse) = testDataHelper.tilbakekreving.persisterOpprettetTilbakekrevingsbehandlingHendelse()

            val actual = testDataHelper.tilbakekreving.tilbakekrevingHendelseRepo.hentForSak(sak.id)
            val actualKravgrunnlag =
                testDataHelper.kravgrunnlagPostgresRepo.hentKravgrunnlagPåSakHendelser(sak.id).also {
                    it.size shouldBe 1
                    it.detaljerSortert.first().kravgrunnlag shouldBe sak.uteståendeKravgrunnlag
                }
            actual shouldBe TilbakekrevingsbehandlingHendelser.create(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                clock = fixedClock,
                hendelser = listOf(hendelse),
                kravgrunnlagPåSak = actualKravgrunnlag,
                dokumentHendelser = DokumentHendelser.empty(sak.id),
            )
        }
    }
}
