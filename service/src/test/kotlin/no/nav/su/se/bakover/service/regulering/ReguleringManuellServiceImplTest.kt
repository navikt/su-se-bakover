package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dokument.domain.Brevtype
import dokument.domain.Dokument
import dokument.domain.brev.BrevService
import dokument.domain.distribuering.Distribueringsadresse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.mottaker.MottakerFnrDomain
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeOppretteManuellRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereManuelt
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.Reguleringsvariant
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.domain.sak.FantIkkeSak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.stansetSøknadsbehandlingMedÅpenRegulering
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.utbetaling.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import person.domain.Person
import person.domain.PersonService
import økonomi.application.utbetaling.UtbetalingService
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.UtbetalingKlargjortForOversendelse
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.Utbetalingsrequest
import java.time.Clock
import java.util.UUID

internal class ReguleringManuellServiceImplTest {

    @Test
    fun `oppretter manuell regulering og lagrer den`() {
        val sak = vedtakSøknadsbehandlingIverksattInnvilget().first
        val reguleringRepo = mock<ReguleringRepo> {
            on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
        }
        val regulerManueltService = lagReguleringManuellServiceImpl(
            sak,
            scrambleUtbetaling = false,
            clock = TikkendeKlokke(fixedClockAt(2.mai(2021))),
            reguleringRepo = reguleringRepo,
        )
        val begrunnelse = "Saksbehandler har opprettet reguleringen manuelt"

        val visning = regulerManueltService.opprettManuellRegulering(
            sakId = sak.id,
            begrunnelse = begrunnelse,
            reguleringsvariant = Reguleringsvariant.ALDERSFRADRAG,
            saksbehandler = saksbehandler,
        ).getOrFail()

        val regulering = visning.regulering.shouldBeInstanceOf<ReguleringUnderBehandling.OpprettetRegulering>()
        regulering.sakId shouldBe sak.id
        regulering.saksnummer shouldBe sak.saksnummer
        regulering.fnr shouldBe sak.fnr
        regulering.sakstype shouldBe sak.type
        regulering.saksbehandler shouldBe saksbehandler
        regulering.reguleringstype shouldBe Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.OpprettetAvSaksbehandler(begrunnelse),
        )
        regulering.reguleringsvariant shouldBe Reguleringsvariant.ALDERSFRADRAG
        regulering.oppgaveId shouldBe null
        visning.gjeldendeVedtaksdata shouldBe regulering.grunnlagsdataOgVilkårsvurderinger
        verify(reguleringRepo).lagre(regulering)
    }

    @Test
    fun `skal ikke kunne opprette manuell regulering før 1 mai`() {
        val sak = vedtakSøknadsbehandlingIverksattInnvilget().first
        val regulerManueltService = lagReguleringManuellServiceImpl(
            sak,
            scrambleUtbetaling = false,
        )

        val resultat = regulerManueltService.opprettManuellRegulering(
            sakId = sak.id,
            begrunnelse = "begrunnelse",
            reguleringsvariant = Reguleringsvariant.GRUNNBELØP,
            saksbehandler = saksbehandler,
        )
        resultat shouldBe KunneIkkeOppretteManuellRegulering.FørMai.left()
    }

    @Test
    fun `skal ikke kunne opprette manuell regulering for sak som ikke finnes`() {
        val sak = vedtakSøknadsbehandlingIverksattInnvilget().first
        val regulerManueltService = lagReguleringManuellServiceImpl(
            sak,
            scrambleUtbetaling = false,
            clock = TikkendeKlokke(fixedClockAt(2.mai(2021))),
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn FantIkkeSak.left()
            },
        )

        val resultat = regulerManueltService.opprettManuellRegulering(
            sakId = sak.id,
            begrunnelse = "begrunnelse",
            reguleringsvariant = Reguleringsvariant.GRUNNBELØP,
            saksbehandler = saksbehandler,
        )
        resultat shouldBe KunneIkkeOppretteManuellRegulering.FantIkkeSak.left()
    }

    @Test
    fun `skal ikke kunne opprette manuell regulering for sak uten vedtak`() {
        val sakUtenVedtak = vedtakSøknadsbehandlingIverksattInnvilget().first.copy(vedtakListe = emptyList())
        val regulerManueltService = lagReguleringManuellServiceImpl(
            sakUtenVedtak,
            scrambleUtbetaling = false,
            clock = TikkendeKlokke(fixedClockAt(2.mai(2021))),
        )

        val resultat = regulerManueltService.opprettManuellRegulering(
            sakId = sakUtenVedtak.id,
            begrunnelse = "begrunnelse",
            reguleringsvariant = Reguleringsvariant.GRUNNBELØP,
            saksbehandler = saksbehandler,
        )
        resultat shouldBe KunneIkkeOppretteManuellRegulering.UgyldigTilstand("Feil med vedtakslinje").left()
    }

    @Test
    fun `full manuell behandling grunnbeløpsregulering fra opprettelse til iverksettelse`() {
        val clock = TikkendeKlokke(fixedClockAt(2.mai(2021)))
        val sak = vedtakSøknadsbehandlingIverksattInnvilget(clock = clock).first
        val lagredeReguleringer = mutableMapOf<ReguleringId, Regulering>()
        val reguleringRepo = mock<ReguleringRepo> {
            on { hent(any()) } doAnswer { lagredeReguleringer[it.getArgument<ReguleringId>(0)] }
            on { lagre(any(), anyOrNull()) } doAnswer {
                val regulering = it.getArgument<Regulering>(0)
                lagredeReguleringer[regulering.id] = regulering
                Unit
            }
            on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
        }
        val regulerManueltService = lagReguleringManuellServiceImpl(
            sak,
            scrambleUtbetaling = false,
            clock = clock,
            reguleringRepo = reguleringRepo,
            oppgaveService = mock<OppgaveService> {
                on { opprettOppgave(any()) } doReturn nyOppgaveHttpKallResponse().right()
                on { lukkOppgave(any(), any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
        )
        val begrunnelse = "Saksbehandler har opprettet reguleringen manuelt"

        val opprettet = regulerManueltService.opprettManuellRegulering(
            sakId = sak.id,
            begrunnelse = begrunnelse,
            reguleringsvariant = Reguleringsvariant.GRUNNBELØP,
            saksbehandler = saksbehandler,
        ).getOrFail().regulering.shouldBeInstanceOf<ReguleringUnderBehandling.OpprettetRegulering>()

        val beregnet = regulerManueltService.beregnReguleringManuelt(
            reguleringId = opprettet.id,
            uføregrunnlag = emptyList(),
            fradrag = emptyList(),
            saksbehandler = saksbehandler,
        ).getOrFail().shouldBeInstanceOf<ReguleringUnderBehandling.BeregnetRegulering>()
        beregnet.reguleringstype shouldBe Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.OpprettetAvSaksbehandler(begrunnelse),
        )

        val tilAttestering = regulerManueltService.reguleringTilAttestering(
            reguleringId = beregnet.id,
            saksbehandler = saksbehandler,
        ).getOrFail().shouldBeInstanceOf<ReguleringUnderBehandling.TilAttestering>()
        tilAttestering.oppgaveId shouldNotBe null

        val iverksatt = regulerManueltService.godkjennRegulering(
            reguleringId = tilAttestering.id,
            attestant = attestant,
        ).getOrFail()
        iverksatt.oppgaveId shouldBe tilAttestering.oppgaveId
        iverksatt.reguleringstype shouldBe Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.OpprettetAvSaksbehandler(begrunnelse),
        )
        iverksatt.attestering.attestant shouldBe attestant
        lagredeReguleringer[iverksatt.id].shouldBeInstanceOf<IverksattRegulering>()
    }

    // TODO legge til mocking av brev etc..

    @Test
    fun `full manuell behandling omregning aldersfradrag fra opprettelse til iverksettelse`() {
        val clock = TikkendeKlokke(fixedClockAt(2.mai(2021)))
        val sak = vedtakSøknadsbehandlingIverksattInnvilget(clock = clock).first
        val lagredeReguleringer = mutableMapOf<ReguleringId, Regulering>()
        val reguleringRepo = mock<ReguleringRepo> {
            on { hent(any()) } doAnswer { lagredeReguleringer[it.getArgument<ReguleringId>(0)] }
            on { lagre(any(), anyOrNull()) } doAnswer {
                val regulering = it.getArgument<Regulering>(0)
                lagredeReguleringer[regulering.id] = regulering
                Unit
            }
            on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
        }
        val regulerManueltService = lagReguleringManuellServiceImpl(
            sak,
            scrambleUtbetaling = false,
            clock = clock,
            reguleringRepo = reguleringRepo,
            oppgaveService = mock<OppgaveService> {
                on { opprettOppgave(any()) } doReturn nyOppgaveHttpKallResponse().right()
                on { lukkOppgave(any(), any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
        )
        val begrunnelse = "Saksbehandler har opprettet reguleringen manuelt"

        val opprettet = regulerManueltService.opprettManuellRegulering(
            sakId = sak.id,
            begrunnelse = begrunnelse,
            reguleringsvariant = Reguleringsvariant.ALDERSFRADRAG,
            saksbehandler = saksbehandler,
        ).getOrFail().regulering.shouldBeInstanceOf<ReguleringUnderBehandling.OpprettetRegulering>()

        val beregnet = regulerManueltService.beregnReguleringManuelt(
            reguleringId = opprettet.id,
            uføregrunnlag = emptyList(),
            fradrag = emptyList(),
            saksbehandler = saksbehandler,
        ).getOrFail().shouldBeInstanceOf<ReguleringUnderBehandling.BeregnetRegulering>()
        beregnet.reguleringstype shouldBe Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.OpprettetAvSaksbehandler(begrunnelse),
        )

        val tilAttestering = regulerManueltService.reguleringTilAttestering(
            reguleringId = beregnet.id,
            saksbehandler = saksbehandler,
        ).getOrFail().shouldBeInstanceOf<ReguleringUnderBehandling.TilAttestering>()
        tilAttestering.oppgaveId shouldNotBe null

        val iverksatt = regulerManueltService.godkjennRegulering(
            reguleringId = tilAttestering.id,
            attestant = attestant,
        ).getOrFail()
        iverksatt.oppgaveId shouldBe tilAttestering.oppgaveId
        iverksatt.reguleringstype shouldBe Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.OpprettetAvSaksbehandler(begrunnelse),
        )
        iverksatt.attestering.attestant shouldBe attestant
        lagredeReguleringer[iverksatt.id].shouldBeInstanceOf<IverksattRegulering>()
    }

    @Test
    fun `manuell behandling av stans skal ikke være lov`() {
        val tikkendeKlokke = TikkendeKlokke(fixedClock)
        val (sak, regulering) = stansetSøknadsbehandlingMedÅpenRegulering(
            regulerFraOgMed = mai(2021),
            clock = tikkendeKlokke,
        )

        val tilAttestering = ReguleringUnderBehandling.TilAttestering(
            saksbehandler = saksbehandler,
            id = regulering.id,
            opprettet = regulering.opprettet,
            sakId = regulering.sakId,
            saksnummer = regulering.saksnummer,
            fnr = regulering.fnr,
            grunnlagsdataOgVilkårsvurderinger = regulering.grunnlagsdataOgVilkårsvurderinger,
            reguleringstype = regulering.reguleringstype,
            reguleringsvariant = regulering.reguleringsvariant,
            sakstype = regulering.sakstype,
            beregning = mock(),
            simulering = mock(),
            attesteringer = regulering.attesteringer,
            eksterntRegulerteBeløp = regulering.eksterntRegulerteBeløp,
            oppgaveId = regulering.oppgaveId,
        )
        val regulerManueltService = lagReguleringManuellServiceImpl(
            sak,
            scrambleUtbetaling = false,
            reguleringRepo = mock<ReguleringRepo> {
                on { hent(any()) } doReturn tilAttestering
            },
        )
        val iverksattRegulering = regulerManueltService.godkjennRegulering(regulering.id, attestant)
        iverksattRegulering shouldBe KunneIkkeRegulereManuelt.StansetYtelseMåStartesFørDenKanReguleres.left()
    }
}

/**
 * @param scrambleUtbetaling Endrer utbetalingslinjene på saken slik at de ikke lenger matcher gjeldendeVedtaksdata. Da tvinger vi fram en ny beregning som har andre beløp enn tidligere utbetalinger.
 */
private fun lagReguleringManuellServiceImpl(
    sak: Sak,
    lagFeilutbetaling: Boolean = false,
    scrambleUtbetaling: Boolean = true,
    clock: Clock = tikkendeFixedClock(),
    sakMedEndringer: Sak = if (scrambleUtbetaling) {
        sak.copy(
            // Endrer utbetalingene for å trigge behov for regulering (hvis ikke vil vi ikke ha beregningsdiff)
            utbetalinger = Utbetalinger(
                oversendtUtbetalingMedKvittering(
                    beregning = beregning(fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt1000())),
                    clock = clock,
                ),
            ),
            // hack det til og snik inn masse fradrag i grunnlaget til saken slik at vi  får fremprovisert en feilutbetaling ved simulering
            vedtakListe = if (lagFeilutbetaling) {
                listOf(
                    (sak.vedtakListe.first() as VedtakInnvilgetSøknadsbehandling).let { vedtak ->
                        VedtakInnvilgetSøknadsbehandling.createFromPersistence(
                            id = vedtak.id,
                            opprettet = vedtak.opprettet,
                            behandling = vedtak.behandling.let {
                                it.copy(
                                    grunnlagsdataOgVilkårsvurderinger = it.grunnlagsdataOgVilkårsvurderinger.oppdaterFradragsgrunnlag(
                                        fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 10000.0)),
                                    ),
                                )
                            },
                            saksbehandler = vedtak.saksbehandler,
                            attestant = vedtak.attestant,
                            periode = vedtak.periode,
                            beregning = vedtak.beregning,
                            simulering = vedtak.simulering,
                            utbetalingId = vedtak.utbetalingId,
                            dokumenttilstand = vedtak.dokumenttilstand,
                        )
                    },
                )
            } else {
                sak.vedtakListe
            },
        )
    } else {
        sak
    },
    sakService: SakService = mock {
        on { hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst() } doReturn listOf(sakMedEndringer.info())
        on { hentSak(any<UUID>()) } doReturn sakMedEndringer.right()
        on { hentSakInfo(any()) } doReturn sakMedEndringer.info().right()
    },
    oppgaveService: OppgaveService = mock(),
    reguleringRepo: ReguleringRepo = mock<ReguleringRepo> {
        on { hent(any()) } doReturn sakMedEndringer.reguleringer.firstOrNull()
        on { hentForSakId(any(), any()) } doReturn sakMedEndringer.reguleringer
        on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
    },
    personService: PersonService = mock {
        on { hentPerson(any(), any()) } doReturn Person(
            ident = Ident(sak.fnr, AktørId("")),
            navn = Person.Navn("", "", ""),
            adresse = listOf(
                Person.Adresse(
                    "",
                    poststed = null,
                    bruksenhet = null,
                    kommune = null,
                    adressetype = "",
                    adresseformat = "",
                    adressenavn = null,
                    husnummer = null,
                    husbokstav = null,
                ),
            ),
        ).right()
    },
    brevService: BrevService = mock {
        on { lagDokumentPdf(any(), any()) } doReturn Dokument.UtenMetadata.Vedtak(
            opprettet = Tidspunkt.now(clock),
            tittel = "vedtaksbrev",
            generertDokument = mock(),
            generertDokumentJson = "",
        ).right()
    },
): ReguleringManuellServiceImpl {
    val nyUtbetaling = UtbetalingKlargjortForOversendelse(
        utbetaling = oversendtUtbetalingUtenKvittering(
            beregning = beregning(
                fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt1000()),
            ),
            clock = clock,
        ),
        callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
            on { it.invoke(any()) } doReturn utbetalingsRequest.right()
        },
    )
    val utbetalingService = mock<UtbetalingService> { service ->
        doAnswer { invocation ->
            simulerUtbetaling(
                utbetalingerPåSak = sakMedEndringer.utbetalinger,
                utbetalingForSimulering = (invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering),
            )
        }.whenever(service).simulerUtbetaling(any())
        on { klargjørUtbetaling(any(), any()) } doReturn nyUtbetaling.right()
    }
    val vedtakService = mock<VedtakService>()
    val sessionFactory = TestSessionFactory()
    val reguleringService = ReguleringServiceImpl(
        reguleringRepo = reguleringRepo,
        utbetalingService = utbetalingService,
        vedtakService = vedtakService,
        sessionFactory = sessionFactory,
        søknadsbehandlingRepo = mock {
            on { hentForSak(sak.id) } doReturn sak.søknadsbehandlinger
        },
        brevService = mock(),
        mottakerService = mock {
            on { hentMottaker(any(), any(), any()) } doReturn MottakerFnrDomain(
                navn = "",
                adresse = Distribueringsadresse(
                    adresselinje1 = "",
                    adresselinje2 = null,
                    adresselinje3 = null,
                    postnummer = "",
                    poststed = "",
                ),
                sakId = sak.id,
                referanseId = UUID.randomUUID(),
                referanseType = ReferanseTypeMottaker.REGULERING,
                brevtype = Brevtype.VEDTAK,
                foedselsnummer = sak.fnr,
            ).right()
        },
        clock = clock,
    )
    return ReguleringManuellServiceImpl(
        sakService = sakService,
        reguleringRepo = reguleringRepo,
        clock = clock,
        reguleringService = reguleringService,
        sessionFactory = sessionFactory,
        statistikkService = mock(),
        oppgaveService = oppgaveService,
        satsFactory = satsFactoryTestPåDato(),
        personService = personService,
        brevService = brevService,
    )
}
