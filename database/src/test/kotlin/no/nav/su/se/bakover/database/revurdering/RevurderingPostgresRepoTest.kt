package no.nav.su.se.bakover.database.revurdering

import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.database.avkorting.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingFerdigbehandlet
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.gjeldendeVedtaksdata
import no.nav.su.se.bakover.test.ikkeSendBrev
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.sendBrev
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulerUtbetaling
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.stønadsperiode2022
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import org.junit.jupiter.api.Test
import java.util.UUID

internal class RevurderingPostgresRepoTest {

    private val revurderingsårsak = Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
    )
    private val oppgaveId = OppgaveId("oppgaveid")
    private val attestant = NavIdentBruker.Attestant("attestant")
    private val simulering = Simulering(
        gjelderId = Fnr.generer(),
        gjelderNavn = "et navn for simulering",
        datoBeregnet = 1.januar(2021),
        nettoBeløp = 200,
        periodeList = listOf(),
    )
    private val informasjonSomRevurderes = InformasjonSomRevurderes.create(
        mapOf(
            Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
            Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
        ),
    )

    private fun opprettet(vedtak: VedtakSomKanRevurderes.EndringIYtelse): OpprettetRevurdering {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = år(2021),
            vedtakListe = nonEmptyListOf(vedtak),
            clock = fixedClock,
        )

        return OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = år(2021),
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtak.id,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
            vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Uhåndtert.IngenUtestående,
            sakinfo = vedtak.sakinfo(),
        )
    }

    private fun beregnetIngenEndring(
        opprettet: OpprettetRevurdering,
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling,
    ) = BeregnetRevurdering.IngenEndring(
        id = opprettet.id,
        periode = opprettet.periode,
        opprettet = opprettet.opprettet,
        tilRevurdering = vedtak.id,
        saksbehandler = opprettet.saksbehandler,
        beregning = vedtak.beregning,
        oppgaveId = opprettet.oppgaveId,
        revurderingsårsak = opprettet.revurderingsårsak,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
        avkorting = opprettet.avkorting.håndter(),
        sakinfo = opprettet.sakinfo,
    )

    private fun beregnetInnvilget(
        opprettet: OpprettetRevurdering,
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling,
    ) = BeregnetRevurdering.Innvilget(
        id = opprettet.id,
        periode = opprettet.periode,
        opprettet = opprettet.opprettet,
        tilRevurdering = vedtak.id,
        saksbehandler = opprettet.saksbehandler,
        beregning = vedtak.beregning,
        oppgaveId = opprettet.oppgaveId,
        revurderingsårsak = opprettet.revurderingsårsak,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
        avkorting = opprettet.avkorting.håndter(),
        sakinfo = opprettet.sakinfo,
    )

    private fun beregnetOpphørt(
        opprettet: OpprettetRevurdering,
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling,
    ) = BeregnetRevurdering.Opphørt(
        id = opprettet.id,
        periode = opprettet.periode,
        opprettet = opprettet.opprettet,
        tilRevurdering = vedtak.id,
        saksbehandler = opprettet.saksbehandler,
        beregning = vedtak.beregning,
        oppgaveId = opprettet.oppgaveId,
        revurderingsårsak = opprettet.revurderingsårsak,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
        avkorting = opprettet.avkorting.håndter(),
        sakinfo = opprettet.sakinfo,
    )

    private fun simulertInnvilget(beregnet: BeregnetRevurdering.Innvilget) = SimulertRevurdering.Innvilget(
        id = beregnet.id,
        periode = beregnet.periode,
        opprettet = beregnet.opprettet,
        tilRevurdering = beregnet.tilRevurdering,
        saksbehandler = beregnet.saksbehandler,
        beregning = beregnet.beregning,
        oppgaveId = beregnet.oppgaveId,
        simulering = simulering,
        revurderingsårsak = beregnet.revurderingsårsak,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
        avkorting = beregnet.avkorting.håndter(),
        tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
        sakinfo = beregnet.sakinfo,
        brevvalgRevurdering = sendBrev(),
    )

    private fun simulertOpphørt(beregnet: BeregnetRevurdering.Opphørt) = SimulertRevurdering.Opphørt(
        id = beregnet.id,
        periode = beregnet.periode,
        opprettet = beregnet.opprettet,
        tilRevurdering = beregnet.tilRevurdering,
        saksbehandler = beregnet.saksbehandler,
        beregning = beregnet.beregning,
        oppgaveId = beregnet.oppgaveId,
        simulering = simulering,
        revurderingsårsak = beregnet.revurderingsårsak,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
        avkorting = beregnet.avkorting.håndter(),
        tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
        sakinfo = beregnet.sakinfo,
        brevvalgRevurdering = sendBrev(),
    )

    @Test
    fun `kan opprette og beregner med ingen endring`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            repo.hent(opprettet.id) shouldBe opprettet

            val beregnetIngenEndring = beregnetIngenEndring(opprettet, vedtak)

            repo.lagre(beregnetIngenEndring)
            repo.hent(opprettet.id) shouldBe beregnetIngenEndring
        }
    }

    @Test
    fun `kan beregne (innvilget) og oppdatere revurdering med ny informasjon`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val (sak, _, vedtak) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    sakInfo = SakInfo(
                        sakId = sak.id,
                        saksnummer = sak.saksnummer,
                        fnr = sak.fnr,
                        type = sak.type,
                    ),
                    stønadsperiode = stønadsperiode2021,
                    sakOgSøknad = sak to søknad,
                )
            }
            val (_, _, etAnnetVedtak) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget(
                sakOgSøknad = testDataHelper.persisterJournalførtSøknadMedOppgave(sakId = sak.id, fnr = sak.fnr),
            ) { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    sakInfo = sak.info(),
                    stønadsperiode = stønadsperiode2022,
                    sakOgSøknad = sak to søknad,
                )
            }

            val opprettetRevurdering = opprettet(vedtak)
            repo.lagre(opprettetRevurdering)
            val innvilgetBeregning = beregnetInnvilget(opprettetRevurdering, vedtak)

            repo.lagre(innvilgetBeregning)
            repo.hent(innvilgetBeregning.id) shouldBe innvilgetBeregning

            val oppdatertRevurdering = innvilgetBeregning.oppdater(
                periode = juni(2020),
                revurderingsårsak = Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("begrunnelse"),
                ),
                grunnlagsdata = opprettetRevurdering.grunnlagsdata,
                vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger,
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
                tilRevurdering = etAnnetVedtak.id,
                avkorting = innvilgetBeregning.avkorting.uhåndtert(),
                saksbehandler = saksbehandler,
            ).getOrFail()

            repo.lagre(oppdatertRevurdering)
            repo.hent(innvilgetBeregning.id) shouldBe oppdatertRevurdering
        }
    }

    @Test
    fun `beregnet ingen endring kan overskrives med ny saksbehandler`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second
            val opprettet = opprettet(vedtak)

            repo.lagre(opprettet)

            val beregnet = beregnetIngenEndring(opprettet, vedtak)

            repo.lagre(beregnet)

            val nyBeregnet = beregnet.copy(
                saksbehandler = Saksbehandler("ny saksbehandler"),
            )

            repo.lagre(nyBeregnet)

            val actual = repo.hent(opprettet.id)

            actual shouldNotBe opprettet
            actual shouldNotBe beregnet
            actual shouldBe nyBeregnet
        }
    }

    @Test
    fun `kan overskrive en beregnet med simulert`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second
            val opprettet = opprettet(vedtak)

            repo.lagre(opprettet)

            val beregnet = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnet)

            val simulert = simulertInnvilget(beregnet)

            repo.lagre(simulert)

            repo.hent(opprettet.id) shouldBe simulert
        }
    }

    @Test
    fun `kan overskrive en simulert med en beregnet`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val (sak, vedtak) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
            val opprettet = opprettetRevurdering(sakOgVedtakSomKanRevurderes = sak to vedtak).second

            repo.lagre(opprettet)

            val beregnet = opprettet.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.gjeldendeVedtaksdata(opprettet.periode.fraOgMed),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail() as BeregnetRevurdering.Innvilget

//            val beregnet = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnet)

            val simulert = beregnet.simuler(
                saksbehandler = saksbehandler,
                clock = fixedClock,
                simuler = { _, _ ->
                    simulerUtbetaling(
                        sak = sak,
                        revurdering = beregnet,
                    ).getOrFail().simulering.right()
                },
            ).getOrFail()

//            val simulert = simulertInnvilget(beregnet)

            repo.lagre(simulert)

            repo.lagre(beregnet)
            repo.hent(opprettet.id) shouldBe beregnet
        }
    }

    @Test
    fun `kan overskrive en simulert med en til attestering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second
            val opprettet = opprettet(vedtak)

            repo.lagre(opprettet)

            val beregnet = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnet)

            val simulert = simulertInnvilget(beregnet)

            repo.lagre(simulert)

            val tilAttestering =
                simulert.tilAttestering(
                    attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                    saksbehandler = saksbehandler,
                ).getOrFail()

            repo.lagre(tilAttestering)

            repo.hent(opprettet.id) shouldBe tilAttestering
        }
    }

    @Test
    fun `saksbehandler som sender til attestering overskriver saksbehandlere som var før`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second
            val opprettet = opprettet(vedtak)

            repo.lagre(opprettet)

            val beregnet = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnet)

            val simulert = simulertInnvilget(beregnet)

            repo.lagre(simulert)

            val tilAttestering = simulert.tilAttestering(
                attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                saksbehandler = Saksbehandler("Ny saksbehandler"),
            ).getOrFail()

            repo.lagre(tilAttestering)

            repo.hent(opprettet.id) shouldBe tilAttestering

            tilAttestering.saksbehandler shouldNotBe opprettet.saksbehandler
        }
    }

    @Test
    fun `kan lagre og hente en iverksatt revurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo as RevurderingPostgresRepo
            val iverksatt = testDataHelper.persisterRevurderingIverksattInnvilget().second

            repo.hent(iverksatt.id) shouldBe iverksatt
            dataSource.withSession {
                repo.hentRevurderingerForSak(iverksatt.sakId, it) shouldBe listOf(iverksatt)
            }
        }
    }

    @Test
    fun `kan lagre og hente en underkjent revurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second
            val opprettet = opprettet(vedtak)

            repo.lagre(opprettet)

            val beregnet = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnet)

            val simulert = simulertInnvilget(beregnet)

            repo.lagre(simulert)

            val tilAttestering =
                simulert.tilAttestering(
                    attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                    saksbehandler = saksbehandler,
                ).getOrFail()
            repo.lagre(tilAttestering)

            val attestering = Attestering.Underkjent(
                attestant = attestant,
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "feil",
                opprettet = fixedTidspunkt,
            )

            val underkjent = tilAttestering.underkjenn(attestering, OppgaveId("nyOppgaveId"))
            repo.lagre(underkjent)

            repo.hent(opprettet.id) shouldBe underkjent
        }
    }

    @Test
    fun `beregnet, simulert og underkjent opphørt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetOpphørt(opprettet, vedtak)
            repo.lagre(beregnet)
            repo.hent(opprettet.id) shouldBe beregnet
            val simulert = simulertOpphørt(beregnet)
            repo.lagre(simulert)
            repo.hent(opprettet.id) shouldBe simulert
            val tilAttestering =
                simulert.tilAttestering(
                    attesteringsoppgaveId = opprettet.oppgaveId,
                    saksbehandler = opprettet.saksbehandler,
                ).getOrFail()
            repo.lagre(tilAttestering)

            val underkjent = UnderkjentRevurdering.Opphørt(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak.id,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                simulering = simulering,
                attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(
                    Attestering.Underkjent(
                        attestant = attestant,
                        grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                        kommentar = "kommentar",
                        opprettet = fixedTidspunkt,
                    ),
                ),
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
                sakinfo = opprettet.sakinfo,
                brevvalgRevurdering = tilAttestering.brevvalgRevurdering,
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id) shouldBe underkjent
        }
    }

    @Test
    fun `til attestering opphørt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetOpphørt(opprettet, vedtak)
            repo.lagre(beregnet)
            repo.lagre(simulertOpphørt(beregnet))
            val underkjent = RevurderingTilAttestering.Opphørt(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak.id,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                simulering = simulering,
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
                sakinfo = opprettet.sakinfo,
                brevvalgRevurdering = ikkeSendBrev(),
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id) shouldBe underkjent
        }
    }

    @Test
    fun `iverksatt opphørt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetOpphørt(opprettet, vedtak)
            repo.lagre(beregnet)
            val simulert = simulertOpphørt(beregnet)
            repo.lagre(simulert)
            val tilAttestering =
                simulert.tilAttestering(
                    attesteringsoppgaveId = opprettet.oppgaveId,
                    saksbehandler = opprettet.saksbehandler,
                ).getOrFail()
            repo.lagre(tilAttestering)

            val underkjent = IverksattRevurdering.Opphørt(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak.id,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                simulering = simulering,
                attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(
                    Attestering.Iverksatt(
                        attestant,
                        fixedTidspunkt,
                    ),
                ),
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                avkorting = AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående,
                tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingFerdigbehandlet,
                sakinfo = opprettet.sakinfo,
                brevvalgRevurdering = sendBrev(),
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id) shouldBe underkjent
        }
    }

    @Test
    fun `beregnet, simulert og underkjent ingen endring`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetIngenEndring(opprettet, vedtak)
            repo.lagre(beregnet)
            repo.hent(opprettet.id) shouldBe beregnet
            val underkjentTilAttestering = RevurderingTilAttestering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak.id,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                grunnlagsdata = opprettet.grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                sakinfo = opprettet.sakinfo,
                brevvalgRevurdering = sendBrev(),
            )
            repo.lagre(underkjentTilAttestering)
            val underkjent = UnderkjentRevurdering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak.id,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(
                    Attestering.Underkjent(
                        attestant = attestant,
                        grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                        kommentar = "kommentar",
                        opprettet = fixedTidspunkt,
                    ),
                ),
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                sakinfo = opprettet.sakinfo,
                brevvalgRevurdering = sendBrev(),
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id) shouldBe underkjent
        }
    }

    @Test
    fun `til attestering ingen endring`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetIngenEndring(opprettet, vedtak)
            repo.lagre(beregnet)
            val underkjent = RevurderingTilAttestering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak.id,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                sakinfo = opprettet.sakinfo,
                brevvalgRevurdering = sendBrev(),
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id) shouldBe underkjent
        }
    }

    @Test
    fun `iverksatt ingen endring`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetIngenEndring(opprettet, vedtak)
            repo.lagre(beregnet)
            val revurderingTilAttestering = RevurderingTilAttestering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak.id,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                grunnlagsdata = opprettet.grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                sakinfo = opprettet.sakinfo,
                brevvalgRevurdering = sendBrev(),
            )
            repo.lagre(revurderingTilAttestering)
            val underkjent = IverksattRevurdering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak.id,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(
                    Attestering.Iverksatt(
                        attestant,
                        fixedTidspunkt,
                    ),
                ),
                grunnlagsdata = opprettet.grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                avkorting = AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående,
                sakinfo = opprettet.sakinfo,
                brevvalgRevurdering = sendBrev(),
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id) shouldBe underkjent
        }
    }

    @Test
    fun `oppdaterer verdier for avkorting ved lagring uten avkorting`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            (repo.hent(opprettet.id) as Revurdering).avkorting shouldBe AvkortingVedRevurdering.Uhåndtert.IngenUtestående

            val beregnet = beregnetInnvilget(opprettet, vedtak)
            repo.lagre(beregnet)
            (repo.hent(opprettet.id) as Revurdering).avkorting shouldBe AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående

            val simulert = simulertInnvilget(beregnet)
            repo.lagre(simulert)
            (repo.hent(opprettet.id) as Revurdering).avkorting shouldBe AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående

            val tilAttestering = simulert.tilAttestering(
                attesteringsoppgaveId = simulert.oppgaveId,
                saksbehandler = saksbehandler,
            ).getOrFail()
            repo.lagre(tilAttestering)
            (repo.hent(opprettet.id) as Revurdering).avkorting shouldBe AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående

            val iverksatt = tilAttestering.tilIverksatt(
                attestant = attestant,
                clock = fixedClock,
                hentOpprinneligAvkorting = { null },
            ).getOrFail()
            repo.lagre(iverksatt)
            (repo.hent(opprettet.id) as Revurdering).avkorting shouldBe AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående
        }
    }

    @Test
    fun `oppdaterer verdier for avkorting ved lagring med avkorting`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val avkortingsvarselRepo = testDataHelper.avkortingsvarselRepo as AvkortingsvarselPostgresRepo
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            (repo.hent(opprettet.id) as Revurdering).avkorting shouldBe AvkortingVedRevurdering.Uhåndtert.IngenUtestående

            val beregnet = beregnetInnvilget(opprettet, vedtak)
            repo.lagre(beregnet)
            (repo.hent(opprettet.id) as Revurdering).avkorting shouldBe AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående

            val avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                    sakId = beregnet.sakId,
                    revurderingId = beregnet.id,
                    simulering = simuleringFeilutbetaling(juni(2021)),
                    opprettet = Tidspunkt.now(fixedClock),
                ),
            )

            val simulert = simulertInnvilget(beregnet).copy(
                avkorting = AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel(
                    avkortingsvarsel = avkortingsvarsel,
                ),
            )
            repo.lagre(simulert)
            (repo.hent(opprettet.id) as Revurdering).avkorting shouldBe AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel(
                avkortingsvarsel = avkortingsvarsel,
            )

            val tilAttestering = simulert.tilAttestering(
                attesteringsoppgaveId = simulert.oppgaveId,
                saksbehandler = saksbehandler,
            ).getOrFail()
            repo.lagre(tilAttestering)
            (repo.hent(opprettet.id) as Revurdering).avkorting shouldBe AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel(
                avkortingsvarsel = avkortingsvarsel,
            )

            testDataHelper.sessionFactory.withSession {
                avkortingsvarselRepo.hent(avkortingsvarsel.id, it) shouldBe null
            }

            val iverksatt = tilAttestering.tilIverksatt(
                attestant = attestant,
                clock = fixedClock,
                hentOpprinneligAvkorting = { avkortingsvarselId ->
                    testDataHelper.avkortingsvarselRepo.hent(avkortingsvarselId)
                },
            ).getOrFail()
            repo.lagre(iverksatt)
            (repo.hent(opprettet.id) as Revurdering).avkorting shouldBe AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel(
                avkortingsvarsel = avkortingsvarsel,
            )
            testDataHelper.sessionFactory.withSession {
                avkortingsvarselRepo.hent(avkortingsvarsel.id, it) shouldBe avkortingsvarsel
            }
        }
    }

    @Test
    fun `hent og lagre brevvalg`() {
        withMigratedDb { dataSource ->
            TestDataHelper(dataSource).also { helper ->
                helper.persisterSimulertRevurdering().second.shouldBeType<SimulertRevurdering.Innvilget>().also {
                    helper.revurderingRepo.lagre(it.copy(brevvalgRevurdering = BrevvalgRevurdering.IkkeValgt))
                    helper.revurderingRepo.hent(it.id)!!.brevvalgRevurdering shouldBe BrevvalgRevurdering.IkkeValgt

                    helper.revurderingRepo.lagre(
                        it.leggTilBrevvalg(
                            sendBrev(
                                fritekst = "fri tekset",
                                begrunnelse = "beggy",
                                bestemtAv = BrevvalgRevurdering.BestemtAv.Systembruker,
                            ),
                        ).getOrFail(),
                    )
                    helper.revurderingRepo.hent(it.id)!!.brevvalgRevurdering shouldBe BrevvalgRevurdering.Valgt.SendBrev(
                        fritekst = "fri tekset",
                        begrunnelse = "beggy",
                        bestemtAv = BrevvalgRevurdering.BestemtAv.Systembruker,
                    )

                    helper.revurderingRepo.lagre(
                        it.leggTilBrevvalg(
                            ikkeSendBrev(
                                begrunnelse = "vil ikke",
                                bestemtAv = BrevvalgRevurdering.BestemtAv.Behandler("kjella"),
                            ),
                        ).getOrFail(),
                    )
                    helper.revurderingRepo.hent(it.id)!!.brevvalgRevurdering shouldBe BrevvalgRevurdering.Valgt.IkkeSendBrev(
                        begrunnelse = "vil ikke",
                        bestemtAv = BrevvalgRevurdering.BestemtAv.Behandler("kjella"),
                    )
                }
            }
        }
    }
}
