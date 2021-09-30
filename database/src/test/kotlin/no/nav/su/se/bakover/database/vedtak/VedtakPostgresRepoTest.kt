package no.nav.su.se.bakover.database.vedtak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.attestant
import no.nav.su.se.bakover.database.beregning
import no.nav.su.se.bakover.database.fixedClock
import no.nav.su.se.bakover.database.fixedTidspunkt
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.simulering
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.vilkårsvurderingerInnvilget
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VedtakPostgresRepoTest {

    @Test
    fun `setter inn og henter vedtak for innvilget stønad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            dataSource.withSession {
                vedtakRepo.hent(vedtak.id, it) shouldBe vedtak
            }
        }
    }

    @Test
    fun `setter inn og henter vedtak for avslått stønad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo
            val søknadsbehandling = testDataHelper.nyIverksattAvslagMedBeregning()
            val vedtak = Vedtak.Avslag.fromSøknadsbehandlingMedBeregning(søknadsbehandling, fixedClock)

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
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

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
            val søknadsbehandlingVedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val nyRevurdering = testDataHelper.nyRevurdering(søknadsbehandlingVedtak, søknadsbehandlingVedtak.periode)
            val iverksattRevurdering = IverksattRevurdering.Innvilget(
                id = nyRevurdering.id,
                periode = søknadsbehandlingVedtak.periode,
                opprettet = nyRevurdering.opprettet,
                tilRevurdering = søknadsbehandlingVedtak,
                saksbehandler = søknadsbehandlingVedtak.saksbehandler,
                oppgaveId = OppgaveId(""),
                beregning = søknadsbehandlingVedtak.beregning,
                simulering = søknadsbehandlingVedtak.simulering,
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                attesteringer = Attesteringshistorikk.empty()
                    .leggTilNyAttestering(Attestering.Iverksatt(søknadsbehandlingVedtak.attestant, Tidspunkt.now())),
                fritekstTilBrev = "",
                revurderingsårsak = Revurderingsårsak(
                    Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                    Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
                ),
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            )
            testDataHelper.revurderingRepo.lagre(iverksattRevurdering)

            val revurderingVedtak = Vedtak.from(iverksattRevurdering, søknadsbehandlingVedtak.utbetalingId, fixedClock)

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
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo
            val (søknadsbehandling, utbetaling) = testDataHelper.nyIverksattInnvilget()
            val vedtakSomErAktivt = Vedtak.fromSøknadsbehandling(søknadsbehandling, utbetaling.id, fixedClock)
                .copy(periode = Periode.create(1.februar(2021), 31.mars(2021)))
            val vedtakUtenforAktivPeriode = Vedtak.fromSøknadsbehandling(søknadsbehandling, utbetaling.id, fixedClock)
                .copy(periode = Periode.create(1.januar(2021), 31.januar(2021)))
            vedtakRepo.lagre(vedtakSomErAktivt)
            vedtakRepo.lagre(vedtakUtenforAktivPeriode)

            val actual = vedtakRepo.hentAktive(1.februar(2021))
            actual.first() shouldBe vedtakSomErAktivt
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom søknadsbehandling og vedtak ved lagring av vedtak for avslått søknadsbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo
            val søknadsbehandling = testDataHelper.nyIverksattAvslagMedBeregning()
            val vedtak = Vedtak.Avslag.fromSøknadsbehandlingMedBeregning(søknadsbehandling, fixedClock)

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
            val vedtakRepo = testDataHelper.vedtakRepo
            val søknadsbehandlingVedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val nyRevurdering = testDataHelper.nyRevurdering(søknadsbehandlingVedtak, søknadsbehandlingVedtak.periode)
            val attestertRevurdering = RevurderingTilAttestering.IngenEndring(
                id = nyRevurdering.id,
                periode = nyRevurdering.periode,
                opprettet = nyRevurdering.opprettet,
                tilRevurdering = søknadsbehandlingVedtak,
                saksbehandler = nyRevurdering.saksbehandler,
                oppgaveId = OppgaveId(""),
                beregning = beregning(nyRevurdering.periode),
                fritekstTilBrev = "",
                revurderingsårsak = Revurderingsårsak(
                    Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                    Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
                ),
                skalFøreTilBrevutsending = true,
                forhåndsvarsel = null,
                grunnlagsdata = nyRevurdering.grunnlagsdata,
                vilkårsvurderinger = nyRevurdering.vilkårsvurderinger,
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
                attesteringer = Attesteringshistorikk.empty(),
            )
            testDataHelper.revurderingRepo.lagre(attestertRevurdering)
            val iverksattRevurdering = IverksattRevurdering.IngenEndring(
                id = nyRevurdering.id,
                periode = nyRevurdering.periode,
                opprettet = nyRevurdering.opprettet,
                tilRevurdering = søknadsbehandlingVedtak,
                saksbehandler = nyRevurdering.saksbehandler,
                oppgaveId = OppgaveId(""),
                beregning = beregning(nyRevurdering.periode),
                attesteringer = Attesteringshistorikk.empty()
                    .leggTilNyAttestering(Attestering.Iverksatt(attestant, Tidspunkt.now())),
                fritekstTilBrev = "",
                revurderingsårsak = Revurderingsårsak(
                    Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                    Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
                ),
                skalFøreTilBrevutsending = true,
                forhåndsvarsel = null,
                grunnlagsdata = nyRevurdering.grunnlagsdata,
                vilkårsvurderinger = nyRevurdering.vilkårsvurderinger,
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            )
            testDataHelper.revurderingRepo.lagre(iverksattRevurdering)

            val revurderingVedtak = Vedtak.from(iverksattRevurdering, fixedClock)

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
            val søknadsbehandling = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val simulertRevurdering = StansAvYtelseRevurdering.SimulertStansAvYtelse(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode2021,
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(),
                vilkårsvurderinger = vilkårsvurderingerInnvilget(),
                tilRevurdering = søknadsbehandling,
                saksbehandler = saksbehandler,
                simulering = simulering(søknadsbehandling.behandling.fnr),
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "huffa",
                ),
            )
            testDataHelper.revurderingRepo.lagre(simulertRevurdering)

            val iverksattRevurdering = simulertRevurdering.iverksett(
                Attestering.Iverksatt(NavIdentBruker.Attestant("atte"), fixedTidspunkt),
            ).getOrFail("Feil i oppsett av testdata")

            testDataHelper.revurderingRepo.lagre(iverksattRevurdering)

            val utbetaling = testDataHelper.nyOversendtUtbetalingMedKvittering().second
            val vedtak = Vedtak.from(iverksattRevurdering, utbetaling.id, fixedClock)

            testDataHelper.vedtakRepo.lagre(vedtak)
            testDataHelper.dataSource.withSession {
                testDataHelper.vedtakRepo.hent(vedtak.id, it) shouldBe vedtak
            }
        }
    }

    @Test
    fun `oppretter og henter vedtak for gjenopptak av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val simulertRevurdering = GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode2021,
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(),
                vilkårsvurderinger = vilkårsvurderingerInnvilget(),
                tilRevurdering = søknadsbehandling,
                saksbehandler = saksbehandler,
                simulering = simulering(søknadsbehandling.behandling.fnr),
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "huffa",
                ),
            )
            testDataHelper.revurderingRepo.lagre(simulertRevurdering)

            val iverksattRevurdering = simulertRevurdering.iverksett(
                Attestering.Iverksatt(NavIdentBruker.Attestant("atte"), fixedTidspunkt),
            )
            testDataHelper.revurderingRepo.lagre(iverksattRevurdering)

            val utbetaling = testDataHelper.nyOversendtUtbetalingMedKvittering().second
            val vedtak = Vedtak.from(iverksattRevurdering, utbetaling.id, fixedClock)

            testDataHelper.vedtakRepo.lagre(vedtak)
            testDataHelper.dataSource.withSession {
                testDataHelper.vedtakRepo.hent(vedtak.id, it) shouldBe vedtak
            }
        }
    }
}
