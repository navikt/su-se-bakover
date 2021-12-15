package no.nav.su.se.bakover.database.revurdering

import arrow.core.getOrHandle
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.beregning.PersistertFradrag
import no.nav.su.se.bakover.database.persistertVariant
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo.ForhåndsvarselDto
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
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
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.iverksattRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertUtbetalingOpphør
import no.nav.su.se.bakover.test.utlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkårsvurderingerAvslåttRevurdering
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
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

    private fun opprettet(vedtak: Vedtak.EndringIYtelse) = OpprettetRevurdering(
        id = UUID.randomUUID(),
        periode = periode2021,
        opprettet = fixedTidspunkt,
        tilRevurdering = vedtak,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = null,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
    )

    private fun beregnetIngenEndring(
        opprettet: OpprettetRevurdering,
        vedtak: Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling,
    ) = BeregnetRevurdering.IngenEndring(
        id = opprettet.id,
        periode = opprettet.periode,
        opprettet = opprettet.opprettet,
        tilRevurdering = vedtak,
        saksbehandler = opprettet.saksbehandler,
        oppgaveId = opprettet.oppgaveId,
        fritekstTilBrev = opprettet.fritekstTilBrev,
        revurderingsårsak = opprettet.revurderingsårsak,
        beregning = vedtak.beregning,
        forhåndsvarsel = null,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
    )

    private fun beregnetInnvilget(
        opprettet: OpprettetRevurdering,
        vedtak: Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling,
    ) = BeregnetRevurdering.Innvilget(
        id = opprettet.id,
        periode = opprettet.periode,
        opprettet = opprettet.opprettet,
        tilRevurdering = vedtak,
        saksbehandler = opprettet.saksbehandler,
        beregning = vedtak.beregning,
        oppgaveId = opprettet.oppgaveId,
        fritekstTilBrev = opprettet.fritekstTilBrev,
        revurderingsårsak = opprettet.revurderingsårsak,
        forhåndsvarsel = null,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
    )

    private fun beregnetOpphørt(
        opprettet: OpprettetRevurdering,
        vedtak: Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling,
    ) = BeregnetRevurdering.Opphørt(
        id = opprettet.id,
        periode = opprettet.periode,
        opprettet = opprettet.opprettet,
        tilRevurdering = vedtak,
        saksbehandler = opprettet.saksbehandler,
        beregning = vedtak.beregning,
        oppgaveId = opprettet.oppgaveId,
        fritekstTilBrev = opprettet.fritekstTilBrev,
        revurderingsårsak = opprettet.revurderingsårsak,
        forhåndsvarsel = null,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
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
        fritekstTilBrev = beregnet.fritekstTilBrev,
        revurderingsårsak = beregnet.revurderingsårsak,
        forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
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
        fritekstTilBrev = beregnet.fritekstTilBrev,
        revurderingsårsak = beregnet.revurderingsårsak,
        forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
        avkortingsvarsel = Avkortingsvarsel.Ingen,
    )

    @Test
    fun `kan opprette og beregner med ingen endring`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            repo.hent(opprettet.id) shouldBe opprettet.persistertVariant()

            val beregnetIngenEndring = beregnetIngenEndring(opprettet, vedtak)

            repo.lagre(beregnetIngenEndring)
            repo.hent(opprettet.id) shouldBe beregnetIngenEndring.persistertVariant()
        }
    }

    @Test
    fun `kan beregne (innvilget) og oppdatere revurdering med ny informasjon`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val etAnnetVedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettetRevurdering = opprettet(vedtak)
            repo.lagre(opprettetRevurdering)
            val innvilgetBeregning = beregnetInnvilget(opprettetRevurdering, vedtak)

            repo.lagre(innvilgetBeregning)
            repo.hent(innvilgetBeregning.id) shouldBe innvilgetBeregning.persistertVariant()

            val oppdatertRevurdering = innvilgetBeregning.oppdater(
                Periode.create(1.juni(2020), 30.juni(2020)),
                Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("begrunnelse"),
                ),
                grunnlagsdata = opprettetRevurdering.grunnlagsdata,
                vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger,
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
                tilRevurdering = etAnnetVedtak,
            )

            repo.lagre(oppdatertRevurdering)
            repo.hent(innvilgetBeregning.id) shouldBe oppdatertRevurdering.persistertVariant()
        }
    }

    @Test
    fun `beregnet ingen endring kan overskrives med ny saksbehandler`() {

        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
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
            actual shouldBe nyBeregnet.persistertVariant()
        }
    }

    @Test
    fun `kan overskrive en beregnet med simulert`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = opprettet(vedtak)

            repo.lagre(opprettet)

            val beregnet = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnet)

            val simulert = simulertInnvilget(beregnet)

            repo.lagre(simulert)

            repo.hent(opprettet.id) shouldBe simulert.persistertVariant()
        }
    }

    @Test
    fun `kan overskrive en simulert med en beregnet`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = opprettet(vedtak)

            repo.lagre(opprettet)

            val beregnet = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnet)

            val simulert = simulertInnvilget(beregnet)

            repo.lagre(simulert)
            repo.lagre(beregnet)
            repo.hent(opprettet.id) shouldBe beregnet.copy(forhåndsvarsel = simulert.forhåndsvarsel).persistertVariant()
        }
    }

    @Test
    fun `kan overskrive en simulert med en til attestering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
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
                    fritekstTilBrev = "fritekst",
                    forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                )

            repo.lagre(tilAttestering)

            repo.hent(opprettet.id) shouldBe tilAttestering.persistertVariant()
        }
    }

    @Test
    fun `saksbehandler som sender til attestering overskriver saksbehandlere som var før`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = opprettet(vedtak)

            repo.lagre(opprettet)

            val beregnet = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnet)

            val simulert = simulertInnvilget(beregnet)

            repo.lagre(simulert)

            val tilAttestering = simulert.tilAttestering(
                attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                saksbehandler = Saksbehandler("Ny saksbehandler"),
                fritekstTilBrev = "fritekst",
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            )

            repo.lagre(tilAttestering)

            repo.hent(opprettet.id) shouldBe tilAttestering.persistertVariant()

            tilAttestering.saksbehandler shouldNotBe opprettet.saksbehandler
        }
    }

    @Test
    fun `kan lagre og hente en iverksatt revurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val uføregrunnlagPostgresRepo = testDataHelper.uføregrunnlagPostgresRepo
            val grunnlagPostgresRepo = testDataHelper.grunnlagRepo
            val repo = testDataHelper.revurderingRepo
            val vedtak =
                testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)

            repo.oppdaterForhåndsvarsel(opprettet.id, Forhåndsvarsel.IngenForhåndsvarsel)

            val tilAttestering = RevurderingTilAttestering.Innvilget(
                id = opprettet.id,
                periode = periode2021,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                beregning = vedtak.beregning,
                simulering = Simulering(
                    gjelderId = Fnr.generer(),
                    gjelderNavn = "Navn Navnesson",
                    datoBeregnet = fixedLocalDate,
                    nettoBeløp = 5,
                    periodeList = listOf(),
                ),
                oppgaveId = oppgaveId,
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                grunnlagsdata = Grunnlagsdata.create(
                    bosituasjon = listOf(
                        Grunnlag.Bosituasjon.Fullstendig.Enslig(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = periode2021,
                            begrunnelse = null,
                        ),
                    ),
                    fradragsgrunnlag = listOf(
                        Grunnlag.Fradragsgrunnlag.tryCreate(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            fradrag =
                            PersistertFradrag(
                                fradragstype = Fradragstype.Introduksjonsstønad,
                                månedsbeløp = 200.0,
                                periode = periode2021,
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ).orNull()!!,
                    ),
                ),
                vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
            )

            grunnlagPostgresRepo.lagreFradragsgrunnlag(
                tilAttestering.id,
                tilAttestering.grunnlagsdata.fradragsgrunnlag,
            )
            grunnlagPostgresRepo.lagreBosituasjongrunnlag(
                behandlingId = tilAttestering.id,
                grunnlag = tilAttestering.grunnlagsdata.bosituasjon,
            )
            testDataHelper.dataSource.withTransaction { tx ->
                uføregrunnlagPostgresRepo.lagre(
                    tilAttestering.id,
                    tilAttestering.vilkårsvurderinger.uføre.grunnlag,
                    tx,
                )
            }

            repo.lagre(tilAttestering)

            val utbetaling = testDataHelper.nyUtbetalingUtenKvittering(
                revurderingTilAttestering = tilAttestering,
            )

            val iverksatt = tilAttestering.tilIverksatt(
                attestant = attestant,
                utbetal = { utbetaling.id.right() },
            ).getOrHandle { throw RuntimeException("Skal ikke kunne skje") }

            repo.lagre(iverksatt)
            repo.hent(iverksatt.id) shouldBe iverksatt.persistertVariant()
            testDataHelper.dataSource.withSession {
                repo.hentRevurderingerForSak(iverksatt.sakId, it) shouldBe listOf(iverksatt.persistertVariant())
            }
        }
    }

    @Test
    fun `kan lagre og hente en underkjent revurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
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
                    fritekstTilBrev = "",
                    forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                )
            repo.lagre(tilAttestering)

            val attestering = Attestering.Underkjent(
                attestant = attestant,
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "feil",
                opprettet = fixedTidspunkt,
            )

            val underkjent = tilAttestering.underkjenn(attestering, OppgaveId("nyOppgaveId"))
            repo.lagre(underkjent)

            repo.hent(opprettet.id) shouldBe underkjent.persistertVariant()
            repo.hentEventuellTidligereAttestering(opprettet.id) shouldBe attestering
        }
    }

    @Test
    fun `beregnet, simulert og underkjent opphørt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetOpphørt(opprettet, vedtak)
            repo.lagre(beregnet)
            repo.hent(opprettet.id) shouldBe beregnet.persistertVariant()
            val simulert = simulertOpphørt(beregnet)
            repo.lagre(simulert)
            repo.hent(opprettet.id) shouldBe simulert.persistertVariant()
            val tilAttestering =
                simulert.tilAttestering(
                    opprettet.oppgaveId,
                    opprettet.saksbehandler,
                    Forhåndsvarsel.IngenForhåndsvarsel,
                    opprettet.fritekstTilBrev,
                ).orNull()!!
            repo.lagre(tilAttestering)

            val underkjent = UnderkjentRevurdering.Opphørt(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
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
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                avkortingsvarsel = Avkortingsvarsel.Ingen,
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id) shouldBe underkjent.persistertVariant()
        }
    }

    @Test
    fun `til attestering opphørt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetOpphørt(opprettet, vedtak)
            repo.lagre(beregnet)
            repo.lagre(simulertOpphørt(beregnet))
            val underkjent = RevurderingTilAttestering.Opphørt(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                simulering = simulering,
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
                avkortingsvarsel = Avkortingsvarsel.Ingen,
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id) shouldBe underkjent.persistertVariant()
        }
    }

    @Test
    fun `iverksatt opphørt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetOpphørt(opprettet, vedtak)
            repo.lagre(beregnet)
            val simulert = simulertOpphørt(beregnet)
            repo.lagre(simulert)
            val tilAttestering = simulert.tilAttestering(
                attesteringsoppgaveId = opprettet.oppgaveId,
                saksbehandler = opprettet.saksbehandler,
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                fritekstTilBrev = opprettet.fritekstTilBrev,
            ).getOrFail()
            repo.lagre(tilAttestering)

            val underkjent = IverksattRevurdering.Opphørt(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                simulering = simulering,
                attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(
                    Attestering.Iverksatt(
                        attestant,
                        fixedTidspunkt,
                    ),
                ),
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                avkortingsvarsel = Avkortingsvarsel.Ingen,
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id) shouldBe underkjent.persistertVariant()
        }
    }

    @Test
    fun `beregnet, simulert og underkjent ingen endring`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetIngenEndring(opprettet, vedtak)
            repo.lagre(beregnet)
            repo.hent(opprettet.id) shouldBe beregnet.persistertVariant()
            val underkjentTilAttestering = RevurderingTilAttestering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                skalFøreTilBrevutsending = false,
                forhåndsvarsel = null,
                grunnlagsdata = opprettet.grunnlagsdata,
                vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
            )
            repo.lagre(underkjentTilAttestering)
            val underkjent = UnderkjentRevurdering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
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
                skalFøreTilBrevutsending = false,
                forhåndsvarsel = null,
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id) shouldBe underkjent.persistertVariant()
        }
    }

    @Test
    fun `til attestering ingen endring`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetIngenEndring(opprettet, vedtak)
            repo.lagre(beregnet)
            val underkjent = RevurderingTilAttestering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                skalFøreTilBrevutsending = true,
                forhåndsvarsel = null,
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id) shouldBe underkjent.persistertVariant()
        }
    }

    @Test
    fun `iverksatt ingen endring`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetIngenEndring(opprettet, vedtak)
            repo.lagre(beregnet)
            val revurderingTilAttestering = RevurderingTilAttestering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                skalFøreTilBrevutsending = false,
                forhåndsvarsel = null,
                grunnlagsdata = opprettet.grunnlagsdata,
                vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
            )
            repo.lagre(revurderingTilAttestering)
            val underkjent = IverksattRevurdering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(
                    Attestering.Iverksatt(
                        attestant,
                        fixedTidspunkt,
                    ),
                ),
                skalFøreTilBrevutsending = false,
                forhåndsvarsel = null,
                grunnlagsdata = opprettet.grunnlagsdata,
                vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
                informasjonSomRevurderes = opprettet.informasjonSomRevurderes,
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id) shouldBe underkjent.persistertVariant()
        }
    }

    @Test
    fun `Lagrer revurdering med ingen forhåndsvarsel`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)

            repo.oppdaterForhåndsvarsel(opprettet.id, Forhåndsvarsel.IngenForhåndsvarsel)

            (repo.hent(opprettet.id) as Revurdering).forhåndsvarsel shouldBe Forhåndsvarsel.IngenForhåndsvarsel
        }
    }

    @Test
    fun `Lagrer revurdering med sendt forhåndsvarsel`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)

            val forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt

            repo.oppdaterForhåndsvarsel(
                opprettet.id,
                forhåndsvarsel,
            )
            (repo.hent(opprettet.id) as Revurdering).forhåndsvarsel shouldBe forhåndsvarsel
        }
    }

    @Test
    fun `Lagrer revurdering med besluttet forhåndsvarsel`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)

            val forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                valg = BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
                begrunnelse = "",
            )

            repo.oppdaterForhåndsvarsel(
                opprettet.id,
                forhåndsvarsel,
            )
            (repo.hent(opprettet.id) as Revurdering).forhåndsvarsel shouldBe forhåndsvarsel
        }
    }

    @Test
    fun `Bare opprettet og simulerte revurderinger kan lagre forhåndsvarsel`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet.copy(forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel))

            (repo.hent(opprettet.id) as Revurdering).forhåndsvarsel shouldBe Forhåndsvarsel.IngenForhåndsvarsel

            val beregnetRevurdering = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnetRevurdering)
            (repo.hent(opprettet.id) as Revurdering).forhåndsvarsel shouldBe Forhåndsvarsel.IngenForhåndsvarsel

            val simulertRevurdering = simulertInnvilget(beregnetRevurdering)
            repo.lagre(simulertRevurdering)
            (repo.hent(simulertRevurdering.id) as Revurdering).forhåndsvarsel shouldBe Forhåndsvarsel.IngenForhåndsvarsel

            val nyOpprettet = opprettet(vedtak)
            repo.lagre(nyOpprettet.copy(forhåndsvarsel = null))

            (repo.hent(nyOpprettet.id) as Revurdering).forhåndsvarsel shouldBe null

            val nyBeregnetRevurdering = beregnetInnvilget(nyOpprettet, vedtak)

            repo.lagre(nyBeregnetRevurdering)
            (repo.hent(nyBeregnetRevurdering.id) as Revurdering).forhåndsvarsel shouldBe null

            val nySimulertRevurdering =
                simulertInnvilget(nyBeregnetRevurdering.copy(forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel))
            repo.lagre(nySimulertRevurdering)
            (repo.hent(nySimulertRevurdering.id) as Revurdering).forhåndsvarsel shouldBe Forhåndsvarsel.IngenForhåndsvarsel
        }
    }

    @Test
    fun `ingen frhåndsvarsel json`() {
        //language=JSON
        val ingenJson = """
            {
              "type": "IngenForhåndsvarsel"
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            ingenJson,
            serialize(ForhåndsvarselDto.from(Forhåndsvarsel.IngenForhåndsvarsel)),
            true,
        )
    }

    @Test
    fun `sendt forhåndsvarsel json`() {
        //language=JSON
        val sendtJson = """
            {
              "type": "Sendt"
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            sendtJson,
            serialize(
                ForhåndsvarselDto.from(
                    Forhåndsvarsel.SkalForhåndsvarsles.Sendt,
                ),
            ),
            true,
        )
    }

    @Test
    fun `besluttet forhåndsvarsel json`() {
        //language=JSON
        val besluttetJson = """
            {
              "type": "Besluttet",
              "valg": "FortsettSammeOpplysninger",
              "begrunnelse": "begrunnelse"
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            besluttetJson,
            serialize(
                ForhåndsvarselDto.from(
                    Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                        BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
                        "begrunnelse",
                    ),
                ),
            ),
            true,
        )
    }

    @Test
    fun `lagrer, og henter en avsluttet revurdering med opprettet som underliggende revurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)

            val persistertOpprettet = testDataHelper.revurderingRepo.hent(opprettet.id) as OpprettetRevurdering

            val avsluttetRevurdering = AvsluttetRevurdering.tryCreate(
                underliggendeRevurdering = persistertOpprettet,
                begrunnelse = "avslutter denne revurderingen",
                fritekst = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrHandle { throw IllegalStateException("Her skulle vi jammen ha en avsluttet revurdering. $it") }

            repo.lagre(avsluttetRevurdering)
            repo.hent(avsluttetRevurdering.id) shouldBe avsluttetRevurdering.persistertVariant()
        }
    }

    @Test
    fun `avsluttet json matcher AvsluttetRevurderingInfo`() {
        //language=JSON
        val avsluttetJson = """
          {
            "fritekst": "en fri tekst", 
            "begrunnelse": "en begrunnelse", 
            "tidspunktAvsluttet": "2021-01-01T01:02:03.456789Z"
          }
        """.trimIndent()

        JSONAssert.assertEquals(
            avsluttetJson,
            serialize(
                AvsluttetRevurderingInfo(
                    begrunnelse = "en begrunnelse",
                    fritekst = "en fri tekst",
                    tidspunktAvsluttet = fixedTidspunkt,
                ),
            ),
            true,
        )
    }

    @Test
    fun `lagrer og henter og oppdaterer revurdering med avkortingsvarsel`() {
        withMigratedDb { dataSource ->
            TestDataHelper(dataSource).also { testDataHelper ->
                val (vedtak, utbetaling) = testDataHelper.vedtakMedInnvilgetSøknadsbehandling(
                    periode = periode2021,
                )

                val (sak, vilkårsvurdert) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak()
                    .let { (sak, revurdering) ->
                        sak.copy(vedtakListe = listOf(vedtak), utbetalinger = listOf(utbetaling)) to revurdering.copy(
                            tilRevurdering = vedtak,
                            vilkårsvurderinger = vilkårsvurderingerAvslåttRevurdering(
                                periode = revurdering.periode,
                                vilkår = utlandsoppholdAvslag(
                                    periode = revurdering.periode,
                                ),
                            ),
                        )
                    }

                testDataHelper.revurderingRepo.lagre(vilkårsvurdert)

                val beregnet = vilkårsvurdert.beregn(sak.utbetalinger, fixedClock)
                    .getOrFail() as BeregnetRevurdering.Opphørt

                testDataHelper.revurderingRepo.lagre(beregnet)

                val simulert = beregnet.toSimulert { sakId, _, opphørsdato ->
                    simulertUtbetalingOpphør(
                        sakId = sakId,
                        periode = beregnet.periode,
                        opphørsdato = opphørsdato,
                        eksisterendeUtbetalinger = sak.utbetalinger,
                    )
                }.getOrFail()

                testDataHelper.revurderingRepo.lagre(simulert)

                val hentetSimulert = (testDataHelper.revurderingRepo.hent(simulert.id) as SimulertRevurdering.Opphørt)
                    .also {
                        it.avkortingsvarsel.let { avkortingsvarsel ->
                            (avkortingsvarsel as Avkortingsvarsel.Utenlandsopphold) shouldBe Avkortingsvarsel.Utenlandsopphold.Opprettet(
                                id = avkortingsvarsel.id,
                                sakId = it.sakId,
                                revurderingId = it.id,
                                opprettet = avkortingsvarsel.opprettet,
                                simulering = avkortingsvarsel.simulering,
                                feilutbetalingslinje = Avkortingsvarsel.Utenlandsopphold.Feilutbetalingslinje(
                                    fraOgMed = 1.januar(2021),
                                    tilOgMed = 31.desember(2021),
                                    forrigeUtbetalingslinjeId = null,
                                    beløp = 25000,
                                    virkningstidspunkt = 1.januar(2021),
                                    uføregrad = Uføregrad.parse(50),
                                ),
                            )
                        }
                    }

                val tilAttestering = hentetSimulert.tilAttestering(
                    attesteringsoppgaveId = oppgaveId,
                    saksbehandler = saksbehandler,
                    forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                    fritekstTilBrev = "jabadoo",
                ).getOrFail()

                testDataHelper.revurderingRepo.lagre(tilAttestering)

                val iverksatt = tilAttestering.tilIverksatt(
                    attestant = attestant,
                    clock = fixedClock,
                    utbetal = { _: UUID, _: NavIdentBruker.Attestant, _: LocalDate, _: Simulering ->
                        UUID30.randomUUID().right()
                    },
                ).getOrFail()

                testDataHelper.revurderingRepo.lagre(iverksatt)

                (testDataHelper.revurderingRepo.hent(iverksatt.id) as IverksattRevurdering.Opphørt).also { opphørt ->
                    opphørt.avkortingsvarsel.let {
                        (it as Avkortingsvarsel.Utenlandsopphold) shouldBe Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                            objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                                id = it.id,
                                sakId = iverksatt.sakId,
                                revurderingId = iverksatt.id,
                                opprettet = it.opprettet,
                                simulering = it.simulering,
                                feilutbetalingslinje = Avkortingsvarsel.Utenlandsopphold.Feilutbetalingslinje(
                                    fraOgMed = 1.januar(2021),
                                    tilOgMed = 31.desember(2021),
                                    forrigeUtbetalingslinjeId = null,
                                    beløp = 25000,
                                    virkningstidspunkt = 1.januar(2021),
                                    uføregrad = Uføregrad.parse(50),
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `sletter avkortingsvarsel dersom vi lagrer ny beregning`() {
        withMigratedDb { dataSource ->
            TestDataHelper(dataSource).also { testDataHelper ->
                val (vedtak, utbetaling) = testDataHelper.vedtakMedInnvilgetSøknadsbehandling(
                    periode = periode2021,
                )

                val (sak, vilkårsvurdert) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak()
                    .let { (sak, revurdering) ->
                        sak.copy(
                            vedtakListe = listOf(vedtak),
                            utbetalinger = listOf(utbetaling),
                        ) to revurdering.copy(
                            tilRevurdering = vedtak,
                            vilkårsvurderinger = vilkårsvurderingerAvslåttRevurdering(
                                periode = revurdering.periode,
                                vilkår = utlandsoppholdAvslag(
                                    periode = revurdering.periode,
                                ),
                            ),
                        )
                    }

                testDataHelper.revurderingRepo.lagre(vilkårsvurdert)

                val beregnet = vilkårsvurdert.beregn(sak.utbetalinger, fixedClock)
                    .getOrFail() as BeregnetRevurdering.Opphørt

                testDataHelper.revurderingRepo.lagre(beregnet)

                val simulert = beregnet.toSimulert { sakId, _, opphørsdato ->
                    simulertUtbetalingOpphør(
                        sakId = sakId,
                        opphørsdato = opphørsdato,
                        periode = beregnet.periode,
                        eksisterendeUtbetalinger = sak.utbetalinger,
                    )
                }.getOrFail()

                testDataHelper.revurderingRepo.lagre(simulert)

                (testDataHelper.revurderingRepo.hent(simulert.id) as SimulertRevurdering.Opphørt)
                    .let { opphørtRevurdering ->
                        opphørtRevurdering.avkortingsvarsel.let { avkortingsvarsel ->
                            (avkortingsvarsel as Avkortingsvarsel.Utenlandsopphold) shouldBe Avkortingsvarsel.Utenlandsopphold.Opprettet(
                                id = avkortingsvarsel.id,
                                sakId = opphørtRevurdering.sakId,
                                revurderingId = opphørtRevurdering.id,
                                opprettet = avkortingsvarsel.opprettet,
                                simulering = avkortingsvarsel.simulering,
                                feilutbetalingslinje = Avkortingsvarsel.Utenlandsopphold.Feilutbetalingslinje(
                                    fraOgMed = 1.januar(2021),
                                    tilOgMed = 31.desember(2021),
                                    forrigeUtbetalingslinjeId = null,
                                    beløp = 25000,
                                    virkningstidspunkt = 1.januar(2021),
                                    uføregrad = Uføregrad.parse(50),
                                ),
                            )
                        }
                    }

                testDataHelper.sessionFactory.withSession { session ->
                    testDataHelper.avkortingsvarselRepo.hentForRevurdering(
                        beregnet.id,
                        session,
                    ) shouldBe beOfType<Avkortingsvarsel.Utenlandsopphold.Opprettet>()

                    testDataHelper.revurderingRepo.lagre(beregnet)

                    testDataHelper.avkortingsvarselRepo.hentForRevurdering(
                        beregnet.id,
                        session,
                    ) shouldBe Avkortingsvarsel.Ingen
                }
            }
        }
    }

    @Test
    fun `oppdater avkortingsvarsel ved lagring av iverksatt revurdering`() {
        val avkortingsvarselRepoMock = mock<AvkortingsvarselPostgresRepo>()
        val iverksattIngenEndring = iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak().second
        val iverksattInnvilget = iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second
        val iverksattOpphør = iverksattRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().second

        val repo = RevurderingPostgresRepo(
            dataSource = mock(),
            fradragsgrunnlagPostgresRepo = mock(),
            bosituasjonsgrunnlagPostgresRepo = mock(),
            uføreVilkårsvurderingRepo = mock(),
            utlandsoppholdVilkårsvurderingRepo = mock(),
            formueVilkårsvurderingRepo = mock(),
            søknadsbehandlingRepo = mock(),
            dbMetrics = mock(),
            sessionFactory = mock(),
            avkortingsvarselRepo = avkortingsvarselRepoMock,
        )

        repo.lagre(
            revurdering = iverksattIngenEndring,
            session = mock(),
        )
        repo.lagre(
            revurdering = iverksattInnvilget,
            session = mock(),
        )
        repo.lagre(
            revurdering = iverksattOpphør,
            session = mock(),
        )

        verify(avkortingsvarselRepoMock, times(3)).lagre(
            revurderingId = eq(revurderingId),
            avkortingsvarsel = eq(Avkortingsvarsel.Ingen),
            tx = any(),
        )
        verifyNoMoreInteractions(avkortingsvarselRepoMock)
    }
}
