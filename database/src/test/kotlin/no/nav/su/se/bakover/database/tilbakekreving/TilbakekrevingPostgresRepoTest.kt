package no.nav.su.se.bakover.database.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåTilbakekrevingsvedtakForsendelse
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.matchendeKravgrunnlag
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import java.util.UUID

internal class TilbakekrevingPostgresRepoTest {

    @Test
    fun `kan lagre og hente uten behov for tilbakekrevingsbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)

            val (sak, vedtak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
            val (_, revurdering) = testDataHelper.persisterRevurderingSimulertInnvilget(sakOgVedtak = sak to vedtak)

            (testDataHelper.revurderingRepo.hent(revurdering.id) as SimulertRevurdering).tilbakekrevingsbehandling shouldBe IkkeBehovForTilbakekrevingUnderBehandling
        }
    }

    @Test
    fun `kan lagre og hente tilbakekrevingsbehandlinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (_, revurdering) = testDataHelper.persisterRevurderingSimulertInnvilget()

            val ikkeAvgjort = IkkeAvgjort(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = revurdering.sakId,
                revurderingId = revurdering.id,
                periode = revurdering.periode,
            )

            testDataHelper.revurderingRepo.lagre(revurdering.copy(tilbakekrevingsbehandling = ikkeAvgjort))

            val tilbakekrevingRepo = testDataHelper.tilbakekrevingRepo as TilbakekrevingPostgresRepo
            testDataHelper.sessionFactory.withSession { session ->
                tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurderingId = revurdering.id,
                    session = session,
                ) shouldBe ikkeAvgjort
            }

            tilbakekrevingRepo.hentAvventerKravgrunnlag() shouldBe emptyList()
            tilbakekrevingRepo.hentMottattKravgrunnlag() shouldBe emptyList()

            val forsto = ikkeAvgjort.tilbakekrev()
            testDataHelper.sessionFactory.withTransaction { tx ->
                tilbakekrevingRepo.lagreTilbakekrevingsbehandling(
                    tilbakrekrevingsbehanding = forsto,
                    tx = tx,
                )
            }

            testDataHelper.sessionFactory.withSession { session ->
                tilbakekrevingRepo.hentTilbakekrevingsbehandling(revurdering.id, session) shouldBe forsto
            }

            tilbakekrevingRepo.hentAvventerKravgrunnlag() shouldBe emptyList()
            tilbakekrevingRepo.hentMottattKravgrunnlag() shouldBe emptyList()

            tilbakekrevingRepo.hentAvventerKravgrunnlag() shouldBe emptyList()
            tilbakekrevingRepo.hentMottattKravgrunnlag() shouldBe emptyList()

            val kunneIkkeForstå = ikkeAvgjort.ikkeTilbakekrev()
            testDataHelper.sessionFactory.withTransaction { tx ->
                tilbakekrevingRepo.lagreTilbakekrevingsbehandling(
                    tilbakrekrevingsbehanding = kunneIkkeForstå,
                    tx = tx,
                )
            }
            testDataHelper.sessionFactory.withSession { session ->
                tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurderingId = revurdering.id,
                    session = session,
                ) shouldBe kunneIkkeForstå
            }

            tilbakekrevingRepo.hentAvventerKravgrunnlag() shouldBe emptyList()
            tilbakekrevingRepo.hentMottattKravgrunnlag() shouldBe emptyList()

            val avventerKravgrunnlag = kunneIkkeForstå.fullførBehandling()
            testDataHelper.sessionFactory.withSession { session ->
                tilbakekrevingRepo.lagreTilbakekrevingsbehandling(
                    tilbakrekrevingsbehanding = avventerKravgrunnlag,
                    session = session,
                )
                tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurderingId = revurdering.id,
                    session = session,
                ) shouldBe avventerKravgrunnlag
            }

            tilbakekrevingRepo.hentAvventerKravgrunnlag() shouldBe listOf(
                avventerKravgrunnlag,
            )
            tilbakekrevingRepo.hentMottattKravgrunnlag() shouldBe emptyList()

            val iverksatt = revurdering.tilAttestering(
                attesteringsoppgaveId = oppgaveIdRevurdering,
                saksbehandler = saksbehandler,
            ).getOrFail().tilIverksatt(
                attestant = attestant,
                hentOpprinneligAvkorting = { null },
                clock = fixedClock,
            ).getOrFail()

            val mottattKravgrunnlag = avventerKravgrunnlag.mottattKravgrunnlag(
                kravgrunnlag = RåttKravgrunnlag("xml"),
                kravgrunnlagMottatt = fixedTidspunkt,
                hentRevurdering = { iverksatt },
                kravgrunnlagMapper = {
                    matchendeKravgrunnlag(
                        iverksatt,
                        iverksatt.simulering,
                        UUID30.randomUUID(),
                        fixedClock,
                    )
                },
            )
            testDataHelper.sessionFactory.withSession { session ->
                tilbakekrevingRepo.lagreTilbakekrevingsbehandling(
                    tilbakrekrevingsbehanding = mottattKravgrunnlag,
                    session = session,
                )
                tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurderingId = revurdering.id,
                    session = session,
                ) shouldBe mottattKravgrunnlag
            }

            tilbakekrevingRepo.hentAvventerKravgrunnlag() shouldBe emptyList()
            tilbakekrevingRepo.hentMottattKravgrunnlag() shouldBe listOf(
                mottattKravgrunnlag,
            )

            val besvartKravgrunnlag = mottattKravgrunnlag.sendtTilbakekrevingsvedtak(
                tilbakekrevingsvedtakForsendelse = RåTilbakekrevingsvedtakForsendelse(
                    "requestXml",
                    fixedTidspunkt,
                    "responseXml",
                ),
            )
            testDataHelper.sessionFactory.withSession { session ->
                tilbakekrevingRepo.lagreTilbakekrevingsbehandling(
                    tilbakrekrevingsbehanding = besvartKravgrunnlag,
                    session = session,
                )
                tilbakekrevingRepo.hentTilbakekrevingsbehandling(
                    revurderingId = revurdering.id,
                    session = session,
                ) shouldBe besvartKravgrunnlag
            }

            tilbakekrevingRepo.hentAvventerKravgrunnlag() shouldBe emptyList()
            tilbakekrevingRepo.hentMottattKravgrunnlag() shouldBe emptyList()
        }
    }
}
