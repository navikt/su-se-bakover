package no.nav.su.se.bakover.database.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.util.UUID

internal class TilbakekrevingPostgresRepoTest {

    @Test
    fun `kan lagre og hente uten behov for tilbakekrevingsbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val revurdering = testDataHelper.simulertInnvilgetRevurdering()

            (testDataHelper.revurderingRepo.hent(revurdering.id) as SimulertRevurdering).tilbakekrevingsbehandling shouldBe IkkeBehovForTilbakekrevingUnderBehandling
        }
    }

    @Test
    fun `kan lagre og hente tilbakekrevingsbehandlinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val revurdering = testDataHelper.simulertInnvilgetRevurdering()

            val ikkeAvgjort = IkkeAvgjort(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = revurdering.sakId,
                revurderingId = revurdering.id,
                periode = revurdering.periode,
            )

            testDataHelper.sessionFactory.withSession { session ->
                testDataHelper.revurderingRepo.lagre(revurdering.copy(tilbakekrevingsbehandling = ikkeAvgjort))
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurdering.id,
                    session,
                ) shouldBe ikkeAvgjort

                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag() shouldBe emptyList()
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandlingerMedUbesvartKravgrunnlag() shouldBe emptyList()

                val forsto = ikkeAvgjort.forsto()
                testDataHelper.tilbakekrevingRepo.lagreTilbakekrevingsbehandling(forsto, session)
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandling(revurdering.id, session) shouldBe forsto

                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag() shouldBe emptyList()
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandlingerMedUbesvartKravgrunnlag() shouldBe emptyList()

                val burdeForstått = ikkeAvgjort.burdeForstått()
                testDataHelper.tilbakekrevingRepo.lagreTilbakekrevingsbehandling(burdeForstått, session)
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurdering.id,
                    session,
                ) shouldBe burdeForstått

                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag() shouldBe emptyList()
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandlingerMedUbesvartKravgrunnlag() shouldBe emptyList()

                val kunneIkkeForstå = ikkeAvgjort.kunneIkkeForstå()
                testDataHelper.tilbakekrevingRepo.lagreTilbakekrevingsbehandling(kunneIkkeForstå, session)
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurdering.id,
                    session,
                ) shouldBe kunneIkkeForstå

                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag() shouldBe emptyList()
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandlingerMedUbesvartKravgrunnlag() shouldBe emptyList()

                val avventerKravgrunnlag = kunneIkkeForstå.ferdigbehandlet()
                testDataHelper.tilbakekrevingRepo.lagreTilbakekrevingsbehandling(avventerKravgrunnlag, session)
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurdering.id,
                    session,
                ) shouldBe avventerKravgrunnlag

                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag() shouldBe listOf(
                    avventerKravgrunnlag,
                )
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandlingerMedUbesvartKravgrunnlag() shouldBe emptyList()

                val mottattKravgrunnlag = avventerKravgrunnlag.mottattKravgrunnlag(
                    kravgrunnlag = RåttKravgrunnlag("xml"),
                    kravgrunnlagMottatt = fixedTidspunkt,
                )
                testDataHelper.tilbakekrevingRepo.lagreTilbakekrevingsbehandling(mottattKravgrunnlag, session)
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurdering.id,
                    session,
                ) shouldBe mottattKravgrunnlag

                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag() shouldBe emptyList()
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandlingerMedUbesvartKravgrunnlag() shouldBe listOf(
                    mottattKravgrunnlag,
                )

                // TODO tilstand for besvart kravgrunnlag
            }
        }
    }
}
