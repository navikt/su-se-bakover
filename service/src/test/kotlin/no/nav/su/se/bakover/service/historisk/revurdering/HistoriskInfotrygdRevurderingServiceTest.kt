package no.nav.su.se.bakover.service.historisk.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.revurdering.domain.Opphørsgrunn
import dokument.domain.Brevtype
import dokument.domain.Dokument
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselDokumentCommand
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdBeløpsperiode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdTidslinjeRepo
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdTidslinjegrunnlag
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdTidslinjevedtak
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskResultat
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadsavgrensning
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregningsgrunnlagForMåned
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdForhåndsvarsel
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdFradragsgrunnlag
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdManueltOpphør
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdMånedskilde
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingRepo
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingStatus
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsbosituasjon
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsvedtak
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsvedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdVedtaksbrevvalg
import no.nav.su.se.bakover.domain.historisk.revurdering.IverksatteMånedsresultater
import no.nav.su.se.bakover.domain.historisk.revurdering.KunneIkkeOppretteHistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskSimuleringClient
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.KunneIkkeSimulereHistoriskUtbetaling
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.dokumentUtenMetadataInformasjonViktig
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.historisk.HistoriskInfotrygdOppdragTestdata
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.Utbetalinger
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

internal class HistoriskInfotrygdRevurderingServiceTest {
    @Test
    fun `forklarer automatisk opphør ved blandet resultat uten å lagre beregningen`() {
        val repo = HistoriskInfotrygdRevurderingRepoFake()
        val service = service(
            TidslinjeRepoFake(projeksjonId, listOf(grunnlag())),
            revurderingRepo = repo,
        )
        val opprettet = service.opprett(command()).shouldBeRight()
        val fradragSomGirOpphør = FradragForMåned(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 50_000.0,
            måned = januar,
            tilhører = FradragTilhører.BRUKER,
        )
        val beregningskommando = BeregnHistoriskInfotrygdRevurderingCommand(
            månedsgrunnlag = beregningskommando().månedsgrunnlag.map { grunnlag ->
                if (grunnlag.måned == fradragSomGirOpphør.måned) {
                    grunnlag.copy(fradrag = listOf(fradragSomGirOpphør))
                } else {
                    grunnlag
                }
            },
        )

        service.beregn(opprettet.id, beregningskommando, saksbehandler).shouldBeLeft() shouldBe
            KunneIkkeBeregneHistoriskInfotrygdRevurderingService.UgyldigGrunnlag(
                "Beregningen gir ytelse i noen måneder og opphør i andre. " +
                    "Fradrag kan gi opphør selv om «Ytelse» er valgt, " +
                    "når beløpet etter fradrag er 0 kroner eller under minstegrensen. " +
                    "Kontroller fradragene. Perioder med ulikt utfall må revurderes hver for seg.",
            )
        repo.hent(opprettet.id) shouldBe opprettet
    }

    @Test
    fun `deaktivert simulering gjør ingen klientkall eller databaseendringer`() {
        val repo = mock<HistoriskInfotrygdRevurderingRepo>()
        val klient = mock<HistoriskSimuleringClient>()
        val service = service(
            projeksjonRepo = mock(),
            revurderingRepo = repo,
            simuleringClient = klient,
            simuleringAktivert = false,
        )
        service.simuler(HistoriskInfotrygdRevurderingId.generer(), saksbehandler).shouldBeLeft() shouldBe
            KunneIkkeSimulereHistoriskRevurderingService.IkkeAktivert
        verifyNoInteractions(repo, klient)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `beregning simulering gjenlesing og attestering uten løpende ytelse`(rentOpphør: Boolean) {
        val repo = HistoriskInfotrygdRevurderingRepoFake()
        val sessionFactory = mock<SessionFactory>()
        val respons = HistoriskInfotrygdOppdragTestdata.simuleringsrespons(fnr, periode)
        val klient = HistoriskSimuleringClient { forespørsel ->
            verifyNoInteractions(sessionFactory)
            forespørsel.harEksisterendeSuOppdrag shouldBe false
            forespørsel.grunnlag.periode shouldBe periode
            forespørsel.grunnlag.månedsresultater.values.all {
                it is HistoriskInfotrygdRevurdertMånedsresultat.Opphør
            } shouldBe rentOpphør
            respons.right()
        }
        val service = service(
            TidslinjeRepoFake(projeksjonId, listOf(grunnlag())),
            revurderingRepo = repo,
            simuleringClient = klient,
            krevSimuleringForAttestering = true,
            sessionFactory = sessionFactory,
        )
        val opprettet = service.opprett(command()).shouldBeRight()
        val beregningskommando = beregningskommando().let { command ->
            command.copy(
                månedsgrunnlag = command.månedsgrunnlag.map {
                    it.copy(manueltOpphør = if (rentOpphør) HistoriskInfotrygdManueltOpphør(Opphørsgrunn.FORMUE) else null)
                },
            )
        }
        service.beregn(opprettet.id, beregningskommando, saksbehandler).shouldBeRight()
        service.oppdaterVedtaksbrev(opprettet.id, HistoriskInfotrygdVedtaksbrevvalg.IKKE_SEND, null, saksbehandler)
            .shouldBeRight()
        service.velgÅIkkeSendeForhåndsvarsel(opprettet.id, saksbehandler).shouldBeRight()
        service.sendTilAttestering(opprettet.id, saksbehandler).shouldBeLeft() shouldBe
            KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(
                KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering.ManglerGyldigSimulering.toString(),
            )

        val simulert = service.simuler(opprettet.id, saksbehandler).shouldBeRight()
        simulert.status shouldBe HistoriskInfotrygdRevurderingStatus.SIMULERT
        simulert.simulering?.respons shouldBe respons
        service.hent(opprettet.id) shouldBe simulert
        service.sendTilAttestering(opprettet.id, saksbehandler).shouldBeRight().status shouldBe
            HistoriskInfotrygdRevurderingStatus.TIL_ATTESTERING
    }

    @Test
    fun `klientfeil og svar uten beregning gir ikke simulert behandling`() {
        val repo = HistoriskInfotrygdRevurderingRepoFake()
        val klient = mock<HistoriskSimuleringClient>()
        val service = service(
            TidslinjeRepoFake(projeksjonId, listOf(grunnlag())),
            revurderingRepo = repo,
            simuleringClient = klient,
        )
        val opprettet = service.opprett(command()).shouldBeRight()
        val beregnet = service.beregn(opprettet.id, beregningskommando(), saksbehandler).shouldBeRight().revurdering
        val feil = KunneIkkeSimulereHistoriskUtbetaling.Klientfeil(SimuleringFeilet.TekniskFeil)
        whenever(klient.simulerHistorisk(any())).thenReturn(feil.left())

        service.simuler(opprettet.id, saksbehandler).shouldBeLeft() shouldBe
            KunneIkkeSimulereHistoriskRevurderingService.SimuleringFeilet(feil)
        service.hent(opprettet.id) shouldBe beregnet

        val respons = HistoriskInfotrygdOppdragTestdata.simuleringsrespons(fnr, periode)
        val responsberegning = requireNotNull(respons.beregning)
        listOf(
            respons.copy(beregning = null),
            respons.copy(beregning = responsberegning.copy(gjelderId = Fnr.generer())),
            respons.copy(beregning = responsberegning.copy(perioder = emptyList())),
        ).forEach { ugyldigRespons ->
            whenever(klient.simulerHistorisk(any())).thenReturn(ugyldigRespons.right())
            service.simuler(opprettet.id, saksbehandler).shouldBeLeft() shouldBe
                KunneIkkeSimulereHistoriskRevurderingService.UgyldigResultat
            service.hent(opprettet.id) shouldBe beregnet
        }
    }

    @Test
    fun `endret beregning under klientkallet blir ikke overskrevet av gammel simulering`() {
        val repo = HistoriskInfotrygdRevurderingRepoFake()
        val klient = mock<HistoriskSimuleringClient>()
        val endretGrunnlag = beregningskommando().let { command ->
            command.copy(
                månedsgrunnlag = command.månedsgrunnlag.map {
                    it.copy(manueltOpphør = HistoriskInfotrygdManueltOpphør(Opphørsgrunn.FORMUE))
                },
            )
        }
        val service = service(
            TidslinjeRepoFake(projeksjonId, listOf(grunnlag())),
            revurderingRepo = repo,
            simuleringClient = klient,
        )
        val id = service.opprett(command()).shouldBeRight().id
        service.beregn(id, beregningskommando(), saksbehandler).shouldBeRight()
        whenever(klient.simulerHistorisk(any())).thenAnswer {
            service.beregn(id, endretGrunnlag, saksbehandler).shouldBeRight()
            HistoriskInfotrygdOppdragTestdata.simuleringsrespons(fnr, periode).right()
        }
        service.simuler(id, saksbehandler).shouldBeLeft() shouldBe
            KunneIkkeSimulereHistoriskRevurderingService.EndretUnderSimulering
        val hentet = requireNotNull(service.hent(id))
        hentet.simulering shouldBe null
        hentet.status shouldBe HistoriskInfotrygdRevurderingStatus.BEREGNET
        hentet.beregning?.erOpphør shouldBe true
    }

    @Test
    fun `ny beregning ugyldiggjør simulering selv ved samme beløp`() {
        val service = service(
            TidslinjeRepoFake(projeksjonId, listOf(grunnlag())),
            simuleringClient = HistoriskSimuleringClient {
                HistoriskInfotrygdOppdragTestdata.simuleringsrespons(fnr, periode).right()
            },
        )
        val id = service.opprett(command()).shouldBeRight().id
        val command = beregningskommando()
        service.beregn(id, command, saksbehandler).shouldBeRight()
        service.simuler(id, saksbehandler).shouldBeRight()
        service.beregn(id, command, saksbehandler).shouldBeRight()
        val hentet = requireNotNull(service.hent(id))
        hentet.simulering shouldBe null
        hentet.harGyldigSimulering() shouldBe false
        hentet.status shouldBe HistoriskInfotrygdRevurderingStatus.BEREGNET
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `endret vedtaksgrunnlag avvises før og etter klientkallet`(endresUnderKallet: Boolean) {
        val repo = spy(HistoriskInfotrygdRevurderingRepoFake())
        val klient = mock<HistoriskSimuleringClient>()
        val service = service(
            TidslinjeRepoFake(projeksjonId, listOf(grunnlag())),
            revurderingRepo = repo,
            simuleringClient = klient,
        )
        val id = service.opprett(command()).shouldBeRight().id
        val beregnet = requireNotNull(service.beregn(id, beregningskommando(), saksbehandler).shouldBeRight().revurdering)
        val nyttVedtak = IverksatteMånedsresultater(
            vedtakId = HistoriskInfotrygdRevurderingsvedtakId(UUID.randomUUID()),
            iverksatt = Tidspunkt.now(clock),
            månedsresultater = requireNotNull(beregnet.beregning).månedsresultater,
        )
        fun endreVedtak() {
            whenever(repo.hentIverksatteMånedsresultater(sakId, periode)).thenReturn(listOf(nyttVedtak))
        }
        if (endresUnderKallet) {
            whenever(klient.simulerHistorisk(any())).thenAnswer {
                endreVedtak()
                HistoriskInfotrygdOppdragTestdata.simuleringsrespons(fnr, periode).right()
            }
        } else {
            endreVedtak()
        }
        val feil = service.simuler(id, saksbehandler).shouldBeLeft()
        (feil is KunneIkkeSimulereHistoriskRevurderingService.UgyldigOppdragsgrunnlag) shouldBe true
        require(feil is KunneIkkeSimulereHistoriskRevurderingService.UgyldigOppdragsgrunnlag)
        (feil.feil is KunneIkkeByggeHistoriskInfotrygdOppdragsgrunnlag.EndretVedtaksgrunnlag) shouldBe true
        service.hent(id) shouldBe beregnet
        if (!endresUnderKallet) verifyNoInteractions(klient)
    }

    @Test
    fun `senere SU-utbetaling hindrer ikke simulering av historisk periode`() {
        val utbetalinger = mock<Utbetalinger> {
            on { isEmpty() } doReturn false
        }
        val utbetalingRepo = mock<UtbetalingRepo> {
            on { hentOversendteUtbetalinger(sakId) } doReturn utbetalinger
        }
        val service = service(
            TidslinjeRepoFake(projeksjonId, listOf(grunnlag())),
            vedtakServiceForInfotrygd = VedtakServiceForInfotrygd { januar(2025) },
            utbetalingRepo = utbetalingRepo,
            simuleringClient = HistoriskSimuleringClient {
                it.harEksisterendeSuOppdrag shouldBe true
                it.grunnlag.periode shouldBe periode
                HistoriskInfotrygdOppdragTestdata.simuleringsrespons(fnr, periode).right()
            },
        )
        val id = service.opprett(command()).shouldBeRight().id
        service.beregn(id, beregningskommando(), saksbehandler).shouldBeRight()
        service.simuler(id, saksbehandler).shouldBeRight().status shouldBe
            HistoriskInfotrygdRevurderingStatus.SIMULERT
    }

    @Test
    fun `oppretter behandling med fastlåste vedtaksreferanser`() {
        val projeksjonRepo = TidslinjeRepoFake(
            projeksjonId = projeksjonId,
            grunnlag = listOf(grunnlag()),
        )
        val revurderingRepo = HistoriskInfotrygdRevurderingRepoFake()
        val service = service(projeksjonRepo, revurderingRepo = revurderingRepo)

        val opprettet = service.opprett(command()).shouldBeRight()

        opprettet.projeksjonId shouldBe projeksjonId
        opprettet.periode shouldBe periode
        opprettet.vedtakSomRevurderesMånedsvis.keys.toList() shouldBe periode.måneder()
        revurderingRepo.hent(opprettet.id) shouldBe opprettet
    }

    @ParameterizedTest
    @CsvSource(
        "2014-12-31, true",
        "2015-01-01, false",
        "2015-01-02, false",
        "null, false",
        nullValues = ["null"],
    )
    fun `varsler bare om mulig forsørgingstillegg ved kjent stønadsstart før 2015`(
        startdato: String?,
        kreverKontroll: Boolean,
    ) {
        val stønadsstart = startdato?.let(LocalDate::parse)
        val historiskGrunnlag = grunnlag().let { grunnlag ->
            grunnlag.copy(
                stønadsavgrensning = grunnlag.stønadsavgrensning.copy(fraOgMed = stønadsstart),
            )
        }
        val service = service(TidslinjeRepoFake(projeksjonId, listOf(historiskGrunnlag)))
        val opprettet = service.opprett(command()).shouldBeRight()

        val måneder = service.hentMånedsgrunnlag(opprettet.id)!!.vedtaksgrunnlagSomRevurderes
        måneder.map { it as HistoriskInfotrygdRevurderingService.HistoriskInfotrygdMånedsgrunnlagForMåned.Ytelse }
            .forEach {
                it.stønadsstart shouldBe stønadsstart
                it.kreverKontrollAvHistoriskForsørgingstillegg shouldBe kreverKontroll
                it.ytelsesbeløp shouldBe historiskBeløp
                it.kilde shouldBe HistoriskInfotrygdMånedskilde.OriginalProjeksjon(projeksjonId)
                it.fradragsgrunnlag shouldBe HistoriskInfotrygdFradragsgrunnlag.OriginaleKoder(
                    historiskGrunnlag.månedsbeløp.single().fradragskoder,
                )
            }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `krever samme kildetype og skiller tidligere SU-vedtak fra ny beregning`(beggeMånederErRevurdert: Boolean) {
        val historiskGrunnlag = grunnlag()
        val tidligereVedtakId = HistoriskInfotrygdRevurderingsvedtakId(UUID.randomUUID())
        val tidligereFradrag = listOf(
            FradragForMåned(
                fradragstype = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 2_000.0,
                måned = februar,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
        val tidligereMånedsresultat = HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
            måned = februar,
            opprinneligStønadId = stønadId,
            opprinneligVedtakId = historiskGrunnlag.vedtak.vedtakId,
            oppdragId = historiskGrunnlag.vedtak.oppdragId,
            bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG,
            sats = BigDecimal(12_000),
            fradrag = tidligereFradrag,
            benyttetRegel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE
                .benyttRegelspesifisering("Tidligere vedtak"),
        )
        val iverksatt = IverksatteMånedsresultater(
            vedtakId = tidligereVedtakId,
            iverksatt = Tidspunkt.now(clock),
            månedsresultater = if (beggeMånederErRevurdert) {
                linkedMapOf(
                    januar to tidligereMånedsresultat.copy(måned = januar, fradrag = emptyList()),
                    februar to tidligereMånedsresultat,
                )
            } else {
                mapOf(februar to tidligereMånedsresultat)
            },
        )
        val revurderingRepo = spy(HistoriskInfotrygdRevurderingRepoFake())
        whenever(revurderingRepo.hentIverksatteMånedsresultater(sakId, periode)).thenReturn(listOf(iverksatt))
        val service = service(
            projeksjonRepo = TidslinjeRepoFake(projeksjonId, listOf(historiskGrunnlag)),
            revurderingRepo = revurderingRepo,
        )
        val resultat = service.opprett(command())
        if (!beggeMånederErRevurdert) {
            resultat.shouldBeLeft() shouldBe KunneIkkeOppretteHistoriskInfotrygdRevurderingService.BlandedeKildetyper
            verify(revurderingRepo, never()).opprett(any())
            return
        }
        val opprettet = resultat.shouldBeRight()
        val førBeregning = requireNotNull(service.hentMånedsgrunnlag(opprettet.id))
        førBeregning.revurdering.beregning shouldBe null

        service.beregn(opprettet.id, beregningskommando(), saksbehandler).shouldBeRight()

        val etterBeregning = requireNotNull(service.hentMånedsgrunnlag(opprettet.id))
        etterBeregning.vedtaksgrunnlagSomRevurderes shouldBe førBeregning.vedtaksgrunnlagSomRevurderes
        val tidligereKilde = HistoriskInfotrygdMånedskilde.Revurderingsvedtak(tidligereVedtakId)
        val revurdertJanuar = etterBeregning.vedtaksgrunnlagSomRevurderes[0]
            as HistoriskInfotrygdRevurderingService.HistoriskInfotrygdMånedsgrunnlagForMåned.Ytelse
        revurdertJanuar.kilde shouldBe tidligereKilde
        val revurdertFebruar = etterBeregning.vedtaksgrunnlagSomRevurderes[1]
            as HistoriskInfotrygdRevurderingService.HistoriskInfotrygdMånedsgrunnlagForMåned.Ytelse
        revurdertFebruar.kilde shouldBe tidligereKilde
        revurdertFebruar.samletFradrag shouldBe BigDecimal("2000.0")
        revurdertFebruar.fradragsgrunnlag shouldBe
            HistoriskInfotrygdFradragsgrunnlag.RevurderteFradrag(tidligereFradrag)
        requireNotNull(etterBeregning.revurdering.beregning).månedsresultater.values.forEach {
            it.fradrag shouldBe emptyList()
        }
    }

    @Test
    fun `beregner og lagrer forhåndsvarsel i samme transaksjon før behandling sendes til attestering`() {
        val revurderingRepo = spy(HistoriskInfotrygdRevurderingRepoFake())
        val dokumentUtenMetadata = dokumentUtenMetadataInformasjonViktig().copy(brevtype = Brevtype.FORHANDSVARSEL)
        val brevService = mock<BrevService> {
            on { lagDokumentPdf(any(), anyOrNull()) } doReturn dokumentUtenMetadata.right()
        }
        val mottakerService = mock<MottakerService> {
            on { hentMottaker(any(), any(), anyOrNull()) } doReturn null.right()
        }
        val sessionFactory = spy(TestSessionFactory())
        val service = service(
            projeksjonRepo = TidslinjeRepoFake(projeksjonId, listOf(grunnlag())),
            revurderingRepo = revurderingRepo,
            brevService = brevService,
            mottakerService = mottakerService,
            sessionFactory = sessionFactory,
        )
        val opprettet = service.opprett(command()).shouldBeRight()
        val beregningskommando = beregningskommando()
        val resultat = service.beregn(opprettet.id, beregningskommando, saksbehandler).shouldBeRight()
        val beregnet = requireNotNull(resultat.revurdering)
        beregnet.status shouldBe HistoriskInfotrygdRevurderingStatus.BEREGNET
        revurderingRepo.hent(opprettet.id) shouldBe beregnet
        resultat.økonomiskRetning shouldBe HistoriskInfotrygdØkonomiskRetning.ETTERBETALING
        resultat.måneder.map { it.måned } shouldBe periode.måneder()
        resultat.måneder.forEach {
            it.gammeltBeløp shouldBe historiskBeløp
            it.nyttBeløp shouldBe BigDecimal(15_952)
            it.differanse shouldBe BigDecimal(6_952)
        }

        service.oppdaterVedtaksbrev(
            opprettet.id,
            HistoriskInfotrygdVedtaksbrevvalg.IKKE_SEND,
            null,
            saksbehandler,
        ).shouldBeRight()
        val fritekst = forhåndsvarselFritekst
        val sendt = service.sendForhåndsvarsel(opprettet.id, fritekst, saksbehandler).shouldBeRight()
        val forventetForhåndsvarsel = HistoriskInfotrygdForhåndsvarsel.Sendt(
            fritekst = fritekst,
            sendtAv = saksbehandler,
            sendt = Tidspunkt.now(clock),
            utdatert = false,
        )
        sendt.forhåndsvarsel shouldBe forventetForhåndsvarsel
        revurderingRepo.hent(opprettet.id) shouldBe sendt
        val dokument = dokumentUtenMetadata.leggTilMetadata(
            Dokument.Metadata(sakId = sakId, historiskRevurderingId = opprettet.id.value),
        )
        val tx = TestSessionFactory.transactionContext
        inOrder(brevService, sessionFactory, mottakerService, revurderingRepo) {
            verify(brevService).lagDokumentPdf(
                eq(
                    ForhåndsvarselDokumentCommand(
                        fødselsnummer = fnr,
                        saksnummer = sakInfo.saksnummer,
                        sakstype = Sakstype.ALDER,
                        saksbehandler = saksbehandler,
                        fritekst = fritekst,
                    ),
                ),
                anyOrNull(),
            )
            verify(sessionFactory).withTransactionContext(any<(TransactionContext) -> Unit>())
            verify(mottakerService).hentMottaker(
                argThat {
                    referanseId == opprettet.id.value &&
                        referanseType == ReferanseTypeMottaker.HISTORISK_INFOTRYGD_REVURDERING &&
                        brevtype == Brevtype.FORHANDSVARSEL
                },
                eq(sakId),
                eq(tx),
            )
            verify(brevService).lagreDokument(dokument, tx)
            verify(revurderingRepo).lagre(sendt, tx)
        }

        val nyBeregning = service.beregn(opprettet.id, beregningskommando, saksbehandler).shouldBeRight()
        val utdatert = requireNotNull(nyBeregning.revurdering)
        utdatert.forhåndsvarsel shouldBe forventetForhåndsvarsel.copy(utdatert = true)
        revurderingRepo.hent(opprettet.id) shouldBe utdatert
        service.sendTilAttestering(opprettet.id, saksbehandler).shouldBeLeft() shouldBe
            KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(
                KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering.ManglerGyldigForhåndsvarsel.toString(),
            )
        revurderingRepo.hent(opprettet.id) shouldBe utdatert

        service.sendForhåndsvarsel(opprettet.id, fritekst, saksbehandler).shouldBeRight()
            .forhåndsvarsel shouldBe forventetForhåndsvarsel
        val tilAttestering = service.sendTilAttestering(opprettet.id, saksbehandler).shouldBeRight()
        tilAttestering.status shouldBe HistoriskInfotrygdRevurderingStatus.TIL_ATTESTERING
        revurderingRepo.hent(opprettet.id) shouldBe tilAttestering
        val attestert = service.attester(opprettet.id, NavIdentBruker.Attestant("A123456")).shouldBeRight()
        attestert.status shouldBe HistoriskInfotrygdRevurderingStatus.ATTESTERT
        revurderingRepo.hent(opprettet.id) shouldBe attestert
    }

    @Test
    fun `PDF-feil ved forhåndsvarsel endrer ikke behandlingen eller starter transaksjon`() {
        val pdfFeil = KunneIkkeLageDokument.FeilVedGenereringAvPdf
        val brevService = mock<BrevService> {
            on { lagDokumentPdf(any(), anyOrNull()) } doReturn pdfFeil.left()
        }
        val revurderingRepo = HistoriskInfotrygdRevurderingRepoFake()
        val sessionFactory = mock<SessionFactory>()
        val mottakerService = mock<MottakerService>()
        val service = service(
            projeksjonRepo = TidslinjeRepoFake(projeksjonId, listOf(grunnlag())),
            revurderingRepo = revurderingRepo,
            brevService = brevService,
            mottakerService = mottakerService,
            sessionFactory = sessionFactory,
        )
        val opprettet = service.opprett(command()).shouldBeRight()
        val beregnet = requireNotNull(
            service.beregn(opprettet.id, beregningskommando(), saksbehandler).shouldBeRight().revurdering,
        )

        service.sendForhåndsvarsel(opprettet.id, forhåndsvarselFritekst, saksbehandler)
            .shouldBeLeft() shouldBe KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel.KunneIkkeGenererePdf(pdfFeil)

        revurderingRepo.hent(opprettet.id) shouldBe beregnet
        verify(brevService, never()).lagreDokument(any(), anyOrNull())
        verifyNoInteractions(sessionFactory, mottakerService)
    }

    @Test
    fun `feil ved dokumentlagring avbryter forhåndsvarsling før behandlingen markeres som sendt`() {
        val dokument = dokumentUtenMetadataInformasjonViktig().copy(brevtype = Brevtype.FORHANDSVARSEL)
        val lagringsfeil = IllegalStateException("Dokumentlagring feilet")
        val brevService = mock<BrevService> {
            on { lagDokumentPdf(any(), anyOrNull()) } doReturn dokument.right()
        }
        doThrow(lagringsfeil).whenever(brevService).lagreDokument(any(), anyOrNull())
        val mottakerService = mock<MottakerService> {
            on { hentMottaker(any(), any(), anyOrNull()) } doReturn null.right()
        }
        val revurderingRepo = HistoriskInfotrygdRevurderingRepoFake()
        val service = service(
            projeksjonRepo = TidslinjeRepoFake(projeksjonId, listOf(grunnlag())),
            revurderingRepo = revurderingRepo,
            brevService = brevService,
            mottakerService = mottakerService,
            sessionFactory = TestSessionFactory(),
        )
        val opprettet = service.opprett(command()).shouldBeRight()
        val beregnet = requireNotNull(
            service.beregn(opprettet.id, beregningskommando(), saksbehandler).shouldBeRight().revurdering,
        )

        assertThrows<IllegalStateException> {
            service.sendForhåndsvarsel(opprettet.id, forhåndsvarselFritekst, saksbehandler)
        } shouldBe lagringsfeil

        revurderingRepo.hent(opprettet.id) shouldBe beregnet
    }

    @Test
    fun `avviser periode etter juni 2026 før sak og ordinære eller historiske vedtak slås opp`() {
        val sakRepo = mock<SakRepo>()
        val projeksjonRepo = mock<HistoriskInfotrygdTidslinjeRepo>()
        val revurderingRepo = mock<HistoriskInfotrygdRevurderingRepo>()
        val vedtakService = mock<VedtakServiceForInfotrygd>()
        val år = 2026
        val sisteHistoriskeMåned = juni(år)
        val førsteSuAppMåned = juli(år)
        val service = service(projeksjonRepo, revurderingRepo, sakRepo, vedtakService)

        service.opprett(
            command(Periode.create(sisteHistoriskeMåned.fraOgMed, førsteSuAppMåned.tilOgMed)),
        ).shouldBeLeft() shouldBe KunneIkkeOppretteHistoriskInfotrygdRevurderingService
            .PeriodenGårForbiSisteHistoriskeMåned(sisteHistoriskeMåned)

        verifyNoInteractions(sakRepo, projeksjonRepo, revurderingRepo, vedtakService)
    }

    @Test
    fun `tillater juni 2026 når ordinær SU starter i juli`() {
        val år = 2026
        val sisteHistoriskeMåned = juni(år)
        val førsteSuAppMåned = juli(år)
        val revurderingRepo = HistoriskInfotrygdRevurderingRepoFake()
        val service = service(
            projeksjonRepo = TidslinjeRepoFake(projeksjonId, listOf(grunnlag(tilOgMed = sisteHistoriskeMåned.tilOgMed))),
            revurderingRepo = revurderingRepo,
            vedtakServiceForInfotrygd = VedtakServiceForInfotrygd { førsteSuAppMåned },
        )

        val opprettet = service.opprett(command(sisteHistoriskeMåned)).shouldBeRight()

        opprettet.periode shouldBe sisteHistoriskeMåned
        revurderingRepo.hent(opprettet.id) shouldBe opprettet
    }

    @Test
    fun `avviser periode som overlapper første innvilgede SU-app-måned`() {
        val førsteSuAppMåned = februar
        val projeksjonRepo = mock<HistoriskInfotrygdTidslinjeRepo>()
        val revurderingRepo = mock<HistoriskInfotrygdRevurderingRepo>()
        val service = service(
            projeksjonRepo = projeksjonRepo,
            revurderingRepo = revurderingRepo,
            vedtakServiceForInfotrygd = VedtakServiceForInfotrygd { førsteSuAppMåned },
        )

        service.opprett(command()).shouldBeLeft() shouldBe
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                .OverlapperInnvilgetSuAppYtelse(førsteSuAppMåned)

        verifyNoInteractions(projeksjonRepo, revurderingRepo)
    }

    @Test
    fun `avviser første måned uten historisk vedtak`() {
        val sakRepo = mock<SakRepo>()
        val revurderingRepo = mock<HistoriskInfotrygdRevurderingRepo>()
        val service = service(
            projeksjonRepo = TidslinjeRepoFake(
                projeksjonId = projeksjonId,
                grunnlag = listOf(grunnlag(tilOgMed = januar.tilOgMed)),
            ),
            sakRepo = sakRepo,
            revurderingRepo = revurderingRepo,
        )

        service.opprett(command()).shouldBeLeft() shouldBe
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                .MånedManglerHistoriskVedtak(februar)

        verify(sakRepo, never()).opprettSak(any(), anyOrNull())
        verifyNoInteractions(revurderingRepo)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 2])
    fun `avviser manglende eller tvetydig månedsbeløp før sak eller behandling opprettes`(antallMånedsbeløp: Int) {
        val historiskGrunnlag = grunnlag().let {
            it.copy(månedsbeløp = List(antallMånedsbeløp) { _ -> it.månedsbeløp.single() })
        }
        val sakRepo = mock<SakRepo>()
        val revurderingRepo = mock<HistoriskInfotrygdRevurderingRepo>()
        val service = service(
            projeksjonRepo = TidslinjeRepoFake(projeksjonId, listOf(historiskGrunnlag)),
            revurderingRepo = revurderingRepo,
            sakRepo = sakRepo,
        )
        val forventetFeil = if (antallMånedsbeløp == 0) {
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService.MånedManglerHistoriskMånedsbeløp(januar)
        } else {
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService.MånedHarFlereHistoriskeMånedsbeløp(januar)
        }

        service.opprett(command()).shouldBeLeft() shouldBe forventetFeil

        verify(sakRepo, never()).opprettSak(any(), anyOrNull())
        verifyNoInteractions(revurderingRepo)
    }

    @Test
    fun `opphørsvedtak trenger ikke månedsbeløp`() {
        val historiskGrunnlag = grunnlag().let {
            it.copy(
                vedtak = it.vedtak.copy(resultat = HistoriskResultat.OPPHØRT),
                månedsbeløp = emptyList(),
            )
        }
        val revurderingRepo = HistoriskInfotrygdRevurderingRepoFake()
        val service = service(
            projeksjonRepo = TidslinjeRepoFake(projeksjonId, listOf(historiskGrunnlag)),
            revurderingRepo = revurderingRepo,
        )

        val opprettet = service.opprett(command()).shouldBeRight()

        opprettet.vedtakSomRevurderesMånedsvis.keys.toList() shouldBe periode.måneder()
        revurderingRepo.hent(opprettet.id) shouldBe opprettet
    }

    @Test
    fun `manglende projeksjon oppretter ikke sak eller behandling`() {
        val sakRepo = mock<SakRepo>()
        val revurderingRepo = mock<HistoriskInfotrygdRevurderingRepo>()
        val service = service(
            projeksjonRepo = mock(),
            revurderingRepo = revurderingRepo,
            sakRepo = sakRepo,
        )

        service.opprett(command()).shouldBeLeft() shouldBe
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService.FantIngenFullførtHistoriskProjeksjon

        verify(sakRepo, never()).opprettSak(any(), anyOrNull())
        verifyNoInteractions(revurderingRepo)
    }

    @Test
    fun `avslutter behandling`() {
        val revurderingRepo = HistoriskInfotrygdRevurderingRepoFake()
        val service = service(
            projeksjonRepo = TidslinjeRepoFake(projeksjonId, listOf(grunnlag())),
            revurderingRepo = revurderingRepo,
        )
        val opprettet = service.opprett(command()).shouldBeRight()

        val avsluttet = service.avslutt(
            id = opprettet.id,
            saksbehandler = NavIdentBruker.Saksbehandler("S654321"),
            begrunnelse = "Behandlingen skal ikke gjennomføres.",
        ).shouldBeRight()

        avsluttet.status shouldBe HistoriskInfotrygdRevurderingStatus.AVSLUTTET
        revurderingRepo.hent(opprettet.id) shouldBe avsluttet
    }

    private fun service(
        projeksjonRepo: HistoriskInfotrygdTidslinjeRepo,
        revurderingRepo: HistoriskInfotrygdRevurderingRepo = HistoriskInfotrygdRevurderingRepoFake(),
        sakRepo: SakRepo = sakRepo(),
        vedtakServiceForInfotrygd: VedtakServiceForInfotrygd = VedtakServiceForInfotrygd { null },
        brevService: BrevService = mock(),
        mottakerService: MottakerService = mock(),
        sessionFactory: SessionFactory = TestSessionFactory(),
        simuleringClient: HistoriskSimuleringClient = mock(),
        utbetalingRepo: UtbetalingRepo = mock {
            on { hentOversendteUtbetalinger(sakId) } doReturn Utbetalinger()
        },
        krevSimuleringForAttestering: Boolean = false,
        simuleringAktivert: Boolean = true,
    ) = HistoriskInfotrygdRevurderingService(
        sakRepo = sakRepo,
        tidslinjeRepo = projeksjonRepo,
        revurderingRepo = revurderingRepo,
        vedtakServiceForInfotrygd = vedtakServiceForInfotrygd,
        brevService = brevService,
        mottakerService = mottakerService,
        sessionFactory = sessionFactory,
        clock = clock,
        simuleringClient = simuleringClient,
        utbetalingRepo = utbetalingRepo,
        simuleringAktivert = simuleringAktivert,
        krevSimuleringForAttestering = krevSimuleringForAttestering,
    )

    private fun sakRepo(): SakRepo = mock {
        on { hentSakInfoForIdent(fnr, Sakstype.ALDER, null) } doReturn sakInfo
        on { hentSakInfo(sakId) } doReturn sakInfo
    }

    private fun command(periode: Periode = HistoriskInfotrygdRevurderingServiceTest.periode) =
        OpprettHistoriskInfotrygdRevurderingCommand(
            fnr = fnr,
            periode = periode,
            saksbehandler = saksbehandler,
        )

    private fun beregningskommando() = BeregnHistoriskInfotrygdRevurderingCommand(
        månedsgrunnlag = periode.måneder().map { måned ->
            HistoriskInfotrygdBeregningsgrunnlagForMåned(
                måned = måned,
                bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG,
                fradrag = emptyList(),
            )
        },
    )

    private class HistoriskInfotrygdRevurderingRepoFake : HistoriskInfotrygdRevurderingRepo {
        private val behandlinger = mutableMapOf<HistoriskInfotrygdRevurderingId, HistoriskInfotrygdRevurdering>()
        private val vedtak = mutableMapOf<HistoriskInfotrygdRevurderingId, HistoriskInfotrygdRevurderingsvedtak>()
        private val transactionContext = mock<TransactionContext>()

        override fun opprett(
            revurdering: HistoriskInfotrygdRevurdering,
        ): Either<KunneIkkeOppretteHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> {
            behandlinger[revurdering.id] = revurdering
            return revurdering.right()
        }

        override fun lagre(
            revurdering: HistoriskInfotrygdRevurdering,
            transactionContext: TransactionContext,
        ) {
            behandlinger[revurdering.id] = revurdering
        }

        override fun hent(id: HistoriskInfotrygdRevurderingId): HistoriskInfotrygdRevurdering? = behandlinger[id]

        override fun hentForSak(sakId: UUID): List<HistoriskInfotrygdRevurdering> =
            behandlinger.values.filter { it.sakId == sakId }

        override fun lagreVedtak(vedtak: HistoriskInfotrygdRevurderingsvedtak) {
            this.vedtak[vedtak.revurderingId] = vedtak
        }

        override fun finnesVedtakForRevurdering(id: HistoriskInfotrygdRevurderingId) = id in vedtak

        override fun hentIverksatteMånedsresultater(
            sakId: UUID,
            periode: Periode,
        ) = emptyList<no.nav.su.se.bakover.domain.historisk.revurdering.IverksatteMånedsresultater>()

        override fun defaultTransactionContext(): TransactionContext = transactionContext
    }

    private class TidslinjeRepoFake(
        private val projeksjonId: UUID,
        private val grunnlag: List<HistoriskInfotrygdTidslinjegrunnlag>,
    ) : HistoriskInfotrygdTidslinjeRepo {
        override fun hentSisteFullførteProjeksjonIdForPerson(personident: String): UUID = projeksjonId

        override fun hentOriginalTidslinjegrunnlag(
            projeksjonId: UUID,
            personident: String,
            periode: Periode,
        ): List<HistoriskInfotrygdTidslinjegrunnlag> = grunnlag
    }

    private companion object {
        val fnr: Fnr = Fnr.generer()
        val sakId: UUID = UUID.randomUUID()
        val projeksjonId: UUID = UUID.randomUUID()
        val år = 2020
        val januar = januar(år)
        val februar = februar(år)
        val periode: Periode = Periode.create(januar.fraOgMed, februar.tilOgMed)
        val stønadId = HistoriskStønadId(1)
        val sakInfo = SakInfo(sakId, Saksnummer(2021L), fnr, Sakstype.ALDER)
        val clock: Clock = Clock.fixed(Instant.parse("2020-03-01T10:00:00Z"), ZoneOffset.UTC)
        val saksbehandler = NavIdentBruker.Saksbehandler("S123456")
        val historiskBeløp = BigDecimal(9_000)
        val forhåndsvarselFritekst = "Varsel om revurdering."

        fun grunnlag(
            tilOgMed: LocalDate = februar.tilOgMed,
        ) = HistoriskInfotrygdTidslinjegrunnlag(
            vedtak = HistoriskInfotrygdTidslinjevedtak(
                stønadId = stønadId,
                vedtakId = HistoriskVedtakId(2),
                oppdragId = "oppdrag-1",
                fraOgMed = januar.fraOgMed,
                tilOgMed = tilOgMed,
                resultat = HistoriskResultat.INNVILGET,
                bosituasjon = HistoriskBosituasjon.ENSLIG,
                registrertTidspunkt = LocalDateTime.parse("2020-01-01T10:00:00"),
                endringskoder = emptyList(),
            ),
            stønadsavgrensning = HistoriskStønadsavgrensning(
                stønadId = stønadId,
                fraOgMed = januar.fraOgMed,
                tilOgMed = tilOgMed,
            ),
            månedsbeløp = listOf(
                HistoriskInfotrygdBeløpsperiode(
                    fraOgMed = januar.fraOgMed,
                    tilOgMed = tilOgMed,
                    sats = BigDecimal(10_000),
                    fradrag = BigDecimal(1_000),
                    fradragskoder = listOf("ARBM"),
                ),
            ),
        )
    }
}
