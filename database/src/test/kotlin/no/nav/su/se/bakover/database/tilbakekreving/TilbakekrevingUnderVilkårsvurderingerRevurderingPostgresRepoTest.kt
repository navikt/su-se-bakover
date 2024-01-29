package no.nav.su.se.bakover.database.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.genererKravgrunnlagFraSimulering
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse
import java.util.UUID

internal class TilbakekrevingUnderVilkårsvurderingerRevurderingPostgresRepoTest {

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
            // Får da feilutbetalingen for januar
            val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
            val testDataHelper = TestDataHelper(dataSource, clock = clock)
            val (_, revurdering) = testDataHelper.persisterRevurderingSimulertOpphørt(
                revurderingsperiode = januar(2021),
            )

            val ikkeAvgjort = IkkeAvgjort(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = revurdering.sakId,
                revurderingId = revurdering.id,
                periode = revurdering.periode,
            )

            testDataHelper.revurderingRepo.lagre(revurdering.copy(tilbakekrevingsbehandling = ikkeAvgjort))

            val tilbakekrevingRepo = testDataHelper.tilbakekreving.tilbakekrevingRepo as TilbakekrevingUnderRevurderingPostgresRepo
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
                saksbehandler = saksbehandler,
            ).getOrFail().tilIverksatt(
                attestant = attestant,
                clock = clock,
            ).getOrFail()

            val mottattKravgrunnlag = avventerKravgrunnlag.mottattKravgrunnlag(
                kravgrunnlag = genererKravgrunnlagFraSimulering(
                    saksnummer = iverksatt.saksnummer,
                    simulering = iverksatt.simulering,
                    // Vi har ikke laget et vedtak her.
                    utbetalingId = UUID30.randomUUID(),
                    clock = clock,
                    // TODO jah: Feltet brukes ikke til noe i dette tilfellet. Denne fila skal slettes når vi fjerner den gamle tilbakekrevingsrutinen.
                    kravgrunnlagPåSakHendelseId = HendelseId.generer(),
                ),
                kravgrunnlagMottatt = fixedTidspunkt,
                hentRevurdering = { iverksatt },
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
                    requestXml = "requestXml",
                    responseXml = "responseXml",
                    tidspunkt = fixedTidspunkt,
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
