package tilbakekreving.infrastructure.repo

import dokument.domain.DokumentHendelser
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelser
import javax.sql.DataSource

/**
 * Disse testene dekker også hentForSak og hent(hendelseId)
 */
@ExtendWith(DbExtension::class)
class OpprettTilbakekrevingsbehandlingRepoTest(private val dataSource: DataSource) {

    @Test
    fun `kan opprette og hente tilbakekrevingsbehandling`() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
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
