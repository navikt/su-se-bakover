package no.nav.su.se.bakover.database.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåTilbakekrevingsvedtakForsendelse
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.matchendeKravgrunnlag
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

                testDataHelper.tilbakekrevingRepo.hentAvventerKravgrunnlag() shouldBe emptyList()
                testDataHelper.tilbakekrevingRepo.hentMottattKravgrunnlag() shouldBe emptyList()

                val forsto = ikkeAvgjort.tilbakekrev()
                testDataHelper.sessionFactory.withTransaction { tx ->
                    testDataHelper.tilbakekrevingRepo.lagreTilbakekrevingsbehandling(forsto, tx)
                }
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandling(revurdering.id, session) shouldBe forsto

                testDataHelper.tilbakekrevingRepo.hentAvventerKravgrunnlag() shouldBe emptyList()
                testDataHelper.tilbakekrevingRepo.hentMottattKravgrunnlag() shouldBe emptyList()

                testDataHelper.tilbakekrevingRepo.hentAvventerKravgrunnlag() shouldBe emptyList()
                testDataHelper.tilbakekrevingRepo.hentMottattKravgrunnlag() shouldBe emptyList()

                val kunneIkkeForstå = ikkeAvgjort.ikkeTilbakekrev()
                testDataHelper.sessionFactory.withTransaction { tx ->
                    testDataHelper.tilbakekrevingRepo.lagreTilbakekrevingsbehandling(kunneIkkeForstå, tx)
                }
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurdering.id,
                    session,
                ) shouldBe kunneIkkeForstå

                testDataHelper.tilbakekrevingRepo.hentAvventerKravgrunnlag() shouldBe emptyList()
                testDataHelper.tilbakekrevingRepo.hentMottattKravgrunnlag() shouldBe emptyList()

                val avventerKravgrunnlag = kunneIkkeForstå.fullførBehandling()
                testDataHelper.tilbakekrevingRepo.lagreTilbakekrevingsbehandling(avventerKravgrunnlag, session)
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurdering.id,
                    session,
                ) shouldBe avventerKravgrunnlag

                testDataHelper.tilbakekrevingRepo.hentAvventerKravgrunnlag() shouldBe listOf(
                    avventerKravgrunnlag,
                )
                testDataHelper.tilbakekrevingRepo.hentMottattKravgrunnlag() shouldBe emptyList()

                // TODO klarer vi å gjøre noe bedre enn å bare jukse her?
                val mottattKravgrunnlag = avventerKravgrunnlag.mottattKravgrunnlag(
                    kravgrunnlag = RåttKravgrunnlag("xml"),
                    kravgrunnlagMottatt = fixedTidspunkt,
                    hentRevurdering = { iverksattRevurdering().second },
                    kravgrunnlagMapper = {
                        matchendeKravgrunnlag(
                            revurdering,
                            revurdering.simulering,
                            UUID30.randomUUID(),
                            fixedClock,
                        )
                    },
                )
                testDataHelper.tilbakekrevingRepo.lagreTilbakekrevingsbehandling(mottattKravgrunnlag, session)
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurdering.id,
                    session,
                ) shouldBe mottattKravgrunnlag

                testDataHelper.tilbakekrevingRepo.hentAvventerKravgrunnlag() shouldBe emptyList()
                testDataHelper.tilbakekrevingRepo.hentMottattKravgrunnlag() shouldBe listOf(
                    mottattKravgrunnlag,
                )

                val besvartKravgrunnlag = mottattKravgrunnlag.sendtTilbakekrevingsvedtak(
                    tilbakekrevingsvedtakForsendelse = RåTilbakekrevingsvedtakForsendelse(
                        "requestXml",
                        fixedTidspunkt,
                        "responseXml",
                    ),
                )
                testDataHelper.tilbakekrevingRepo.lagreTilbakekrevingsbehandling(besvartKravgrunnlag, session)
                testDataHelper.tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurdering.id,
                    session,
                ) shouldBe besvartKravgrunnlag

                testDataHelper.tilbakekrevingRepo.hentAvventerKravgrunnlag() shouldBe emptyList()
                testDataHelper.tilbakekrevingRepo.hentMottattKravgrunnlag() shouldBe emptyList()
            }
        }
    }
}
