package no.nav.su.se.bakover.database.vedtak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingFerdigbehandlet
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

internal class VedtakPostgresRepoTest {

    @Test
    fun `setter inn og henter vedtak for innvilget stønad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
            val vedtak =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second

            dataSource.withSession {
                vedtakRepo.hent(vedtak.id, it) shouldBe vedtak
            }
        }
    }

    @Test
    fun `setter inn og henter vedtak for avslått stønad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning().second
            val vedtak = Avslagsvedtak.fromSøknadsbehandlingMedBeregning(søknadsbehandling, fixedClock)

            vedtakRepo.lagre(vedtak)

            dataSource.withSession {
                vedtakRepo.hent(vedtak.id, it) shouldBe vedtak
            }
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom søknadsbehandling og vedtak ved lagring av vedtak for søknadsbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtak =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second

            dataSource.withSession { session ->
                """
                    SELECT søknadsbehandlingId, revurderingId from behandling_vedtak where vedtakId = :vedtakId
                """.trimIndent()
                    .hent(mapOf("vedtakId" to vedtak.id), session) {
                        it.stringOrNull("søknadsbehandlingId") shouldBe vedtak.behandling.id.toString()
                        it.stringOrNull("revurderingId") shouldBe null
                    }
            }
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom revurdering og vedtak ved lagring av vedtak for revurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo
            val (sak, søknadsbehandlingVedtak) = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().let { it.first to it.second }

            val nyRevurdering = testDataHelper.persisterRevurderingOpprettet(sak to søknadsbehandlingVedtak).second
            val iverksattRevurdering = IverksattRevurdering.Innvilget(
                id = nyRevurdering.id,
                periode = søknadsbehandlingVedtak.periode,
                opprettet = nyRevurdering.opprettet,
                tilRevurdering = søknadsbehandlingVedtak.id,
                saksbehandler = søknadsbehandlingVedtak.saksbehandler,
                oppgaveId = OppgaveId(""),
                beregning = søknadsbehandlingVedtak.beregning,
                simulering = søknadsbehandlingVedtak.simulering,
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                attesteringer = Attesteringshistorikk.empty()
                    .leggTilNyAttestering(Attestering.Iverksatt(søknadsbehandlingVedtak.attestant, fixedTidspunkt)),
                fritekstTilBrev = "",
                revurderingsårsak = Revurderingsårsak(
                    Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                    Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
                ),
                forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
                vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
                avkorting = AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående,
                tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingFerdigbehandlet,
                sakinfo = søknadsbehandlingVedtak.sakinfo(),
            )
            testDataHelper.revurderingRepo.lagre(iverksattRevurdering)

            val revurderingVedtak =
                VedtakSomKanRevurderes.from(iverksattRevurdering, søknadsbehandlingVedtak.utbetalingId, fixedClock)

            vedtakRepo.lagre(revurderingVedtak)

            dataSource.withSession { session ->
                """
                    SELECT søknadsbehandlingId, revurderingId from behandling_vedtak where vedtakId = :vedtakId
                """.trimIndent()
                    .hent(mapOf("vedtakId" to revurderingVedtak.id), session) {
                        it.stringOrNull("søknadsbehandlingId") shouldBe null
                        it.stringOrNull("revurderingId") shouldBe iverksattRevurdering.id.toString()
                    }
            }
        }
    }

    @Test
    fun `hent alle aktive vedtak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(
                dataSource = dataSource,
                clock = fixedClock.plus(31, ChronoUnit.DAYS),
            )
            val vedtakRepo = testDataHelper.vedtakRepo
            // Persisterer et ikke-aktivt vedtak
            testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                stønadsperiode = Stønadsperiode.create(januar(2021)),
            )
            val vedtakSomErAktivt =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                    stønadsperiode = Stønadsperiode.create(Periode.create(1.februar(2021), 31.mars(2021))),
                ).second
            vedtakRepo.hentAktive(1.februar(2021)).also {
                it.size shouldBe 1
                it.first() shouldBe vedtakSomErAktivt
            }
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom søknadsbehandling og vedtak ved lagring av vedtak for avslått søknadsbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning().second
            val vedtak = Avslagsvedtak.fromSøknadsbehandlingMedBeregning(søknadsbehandling, fixedClock)

            vedtakRepo.lagre(vedtak)

            dataSource.withSession { session ->
                """
                    SELECT søknadsbehandlingId, revurderingId from behandling_vedtak where vedtakId = :vedtakId
                """.trimIndent()
                    .hent(mapOf("vedtakId" to vedtak.id), session) {
                        it.stringOrNull("søknadsbehandlingId") shouldBe søknadsbehandling.id.toString()
                        it.stringOrNull("revurderingId") shouldBe null
                    }
            }
        }
    }

    @Test
    fun `kan lagre et vedtak som ikke fører til endring i utbetaling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
            val (sak, søknadsbehandlingVedtak) = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering()

            val (_, nyRevurdering) = testDataHelper.persisterRevurderingOpprettet(sak to søknadsbehandlingVedtak)

            val beregning = nyRevurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = nyRevurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().beregning

            val attestertRevurdering = RevurderingTilAttestering.IngenEndring(
                id = nyRevurdering.id,
                periode = nyRevurdering.periode,
                opprettet = nyRevurdering.opprettet,
                tilRevurdering = søknadsbehandlingVedtak.id,
                saksbehandler = nyRevurdering.saksbehandler,
                oppgaveId = OppgaveId(""),
                beregning = beregning,
                fritekstTilBrev = "",
                revurderingsårsak = Revurderingsårsak(
                    Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                    Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
                ),
                skalFøreTilUtsendingAvVedtaksbrev = true,
                forhåndsvarsel = null,
                grunnlagsdata = nyRevurdering.grunnlagsdata,
                vilkårsvurderinger = nyRevurdering.vilkårsvurderinger,
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                sakinfo = søknadsbehandlingVedtak.sakinfo(),
            )
            testDataHelper.revurderingRepo.lagre(attestertRevurdering)
            val iverksattRevurdering = IverksattRevurdering.IngenEndring(
                id = nyRevurdering.id,
                periode = nyRevurdering.periode,
                opprettet = nyRevurdering.opprettet,
                tilRevurdering = søknadsbehandlingVedtak.id,
                saksbehandler = nyRevurdering.saksbehandler,
                oppgaveId = OppgaveId(""),
                beregning = beregning,
                attesteringer = Attesteringshistorikk.empty()
                    .leggTilNyAttestering(Attestering.Iverksatt(attestant, fixedTidspunkt)),
                fritekstTilBrev = "",
                revurderingsårsak = Revurderingsårsak(
                    Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                    Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
                ),
                skalFøreTilUtsendingAvVedtaksbrev = true,
                forhåndsvarsel = null,
                grunnlagsdata = nyRevurdering.grunnlagsdata,
                vilkårsvurderinger = nyRevurdering.vilkårsvurderinger,
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
                avkorting = AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående,
                sakinfo = søknadsbehandlingVedtak.sakinfo(),
            )
            testDataHelper.revurderingRepo.lagre(iverksattRevurdering)

            val revurderingVedtak = VedtakSomKanRevurderes.from(iverksattRevurdering, fixedClock)

            vedtakRepo.lagre(revurderingVedtak)

            dataSource.withSession {
                vedtakRepo.hent(revurderingVedtak.id, it) shouldBe revurderingVedtak
            }

            dataSource.withSession { session ->
                """
                    SELECT søknadsbehandlingId, revurderingId from behandling_vedtak where vedtakId = :vedtakId
                """.trimIndent()
                    .hent(mapOf("vedtakId" to revurderingVedtak.id), session) {
                        it.stringOrNull("søknadsbehandlingId") shouldBe null
                        it.stringOrNull("revurderingId") shouldBe iverksattRevurdering.id.toString()
                    }
            }
        }
    }

    @Test
    fun `oppretter og henter vedtak for stans av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtak = testDataHelper.persisterVedtakForStans()
            val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
            testDataHelper.dataSource.withSession {
                vedtakRepo.hent(vedtak.id, it) shouldBe vedtak
            }
        }
    }

    @Test
    fun `oppretter og henter vedtak for gjenopptak av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtak = testDataHelper.persisterVedtakForGjenopptak()
            val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
            testDataHelper.dataSource.withSession {
                vedtakRepo.hent(vedtak.id, it) shouldBe vedtak
            }
        }
    }
}
