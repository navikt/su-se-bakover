package no.nav.su.se.bakover.service

import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.GenererDokumentCommand
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.brev.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.domain.brev.command.PåminnelseNyStønadsperiodeDokumentCommand
import no.nav.su.se.bakover.domain.jobcontext.NameAndYearMonthId
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.stønadsperiode.SendPåminnelseNyStønadsperiodeJobRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.grunnlag.formueGrunnlagUtenEpsAvslått
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.Clock
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

internal class SendPåminnelserOmNyStønadsperiodeServiceImplTest {
    @Test
    fun `hopper over saker som feiler og lagrer saker som er ok`() {
        val desemberClock =
            Clock.fixed(11.desember(2021).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

        val (sak1, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = Saksnummer(3000),
        )
        val (sak2, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = Saksnummer(3001),
        )
        val (sak3, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = Saksnummer(3002),
        )

        SendPåminnelseNyStønadsperiodeServiceAndMocks(
            clock = desemberClock,
            sakRepo = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSaker() } doReturn listOf(
                    SakInfo(sak1.id, sak1.saksnummer, sak1.fnr, sak1.type),
                    SakInfo(sak2.id, sak2.saksnummer, sak2.fnr, sak2.type),
                    SakInfo(sak3.id, sak3.saksnummer, sak3.fnr, sak3.type),
                )
                on { hentSak(any<Saksnummer>()) } doReturnConsecutively listOf(
                    sak1,
                    sak2,
                    null,
                )
            },
            sessionFactory = TestSessionFactory(),
            brevService = mock {
                on { lagDokument(any<GenererDokumentCommand>()) } doReturnConsecutively listOf(
                    KunneIkkeLageDokument.FeilVedGenereringAvPdf.left(),
                    Dokument.UtenMetadata.Informasjon.Viktig(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(desemberClock),
                        tittel = PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel(),
                        generertDokument = PdfA("pdf".toByteArray()),
                        generertDokumentJson = "{}",
                    ).right(),
                )
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            sendPåminnelseNyStønadsperiodeJobRepo = mock {
                on { hent(any()) } doReturn null
            },
            formuegrenserFactory = formuegrenserFactoryTestPåDato(),
        ).let { serviceAndMocks ->
            val expectedContext = SendPåminnelseNyStønadsperiodeContext(
                clock = desemberClock,
                id = NameAndYearMonthId(
                    name = "SendPåminnelseNyStønadsperiode",
                    yearMonth = YearMonth.of(2021, Month.DECEMBER),
                ),
                opprettet = Tidspunkt.now(desemberClock),
                endret = Tidspunkt.now(desemberClock),
                prosessert = setOf(
                    Saksnummer(3001),
                ),
                sendt = setOf(
                    Saksnummer(3001),
                ),
                feilet = listOf(
                    SendPåminnelseNyStønadsperiodeContext.Feilet(
                        Saksnummer(3000),
                        SendPåminnelseNyStønadsperiodeContext.KunneIkkeSendePåminnelse.KunneIkkeLageBrev.toString(),
                    ),
                    SendPåminnelseNyStønadsperiodeContext.Feilet(
                        Saksnummer(3002),
                        NullPointerException().toString(),
                    ),
                ),
            )

            serviceAndMocks.service.sendPåminnelser() shouldBe expectedContext

            val captor = argumentCaptor<GenererDokumentCommand>()
            verify(serviceAndMocks.brevService, times(2)).lagDokument(captor.capture())

            captor.lastValue shouldBe PåminnelseNyStønadsperiodeDokumentCommand(
                saksnummer = Saksnummer(3001),
                utløpsdato = LocalDate.of(2021, Month.DECEMBER, 31),
                halvtGrunnbeløp = 50676,
                fødselsnummer = sak2.fnr,
            )

            verify(serviceAndMocks.brevService).lagreDokument(
                dokument = argThat {
                    it.tittel shouldBe PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel()
                    it.metadata shouldBe Dokument.Metadata(
                        sakId = sak2.id,
                    )
                },
                transactionContext = argThat { it shouldBe serviceAndMocks.sessionFactory.newTransactionContext() },
            )
            verify(serviceAndMocks.sendPåminnelseNyStønadsperiodeJobRepo).hent(
                SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(
                    desemberClock,
                ),
            )
            val lagreCaptor = argumentCaptor<SendPåminnelseNyStønadsperiodeContext>()
            verify(serviceAndMocks.sendPåminnelseNyStønadsperiodeJobRepo, times(3)).lagre(
                context = lagreCaptor.capture(),
                transactionContext = argThat { it shouldBe serviceAndMocks.sessionFactory.newTransactionContext() },
            )
            lagreCaptor.lastValue shouldBe expectedContext
        }
    }

    @Test
    fun `oppdaterer eksisterende context med ny informasjon`() {
        val desemberClock =
            Clock.fixed(11.desember(2021).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

        val (sak1, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = Saksnummer(3003),
        )

        SendPåminnelseNyStønadsperiodeServiceAndMocks(
            clock = desemberClock,
            sakRepo = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSaker() } doReturn listOf(
                    SakInfo(sak1.id, sak1.saksnummer, sak1.fnr, sak1.type),
                )
                on { hentSak(any<Saksnummer>()) } doReturnConsecutively listOf(
                    sak1,
                )
            },
            sessionFactory = TestSessionFactory(),
            brevService = mock {
                on { lagDokument(any<GenererDokumentCommand>()) } doReturnConsecutively listOf(
                    Dokument.UtenMetadata.Informasjon.Viktig(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(desemberClock),
                        tittel = PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel(),
                        generertDokument = PdfA("pdf".toByteArray()),
                        generertDokumentJson = "{}",
                    ).right(),
                )
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            sendPåminnelseNyStønadsperiodeJobRepo = mock {
                on { hent(any()) } doReturn
                    SendPåminnelseNyStønadsperiodeContext(
                        clock = desemberClock,
                        id = NameAndYearMonthId(
                            name = "SendPåminnelseNyStønadsperiode",
                            yearMonth = YearMonth.of(2021, Month.DECEMBER),
                        ),
                        opprettet = Tidspunkt.now(desemberClock),
                        endret = Tidspunkt.now(desemberClock),
                        prosessert = setOf(
                            Saksnummer(3000),
                            Saksnummer(3001),
                            Saksnummer(3002),
                        ),
                        sendt = setOf(
                            Saksnummer(3000),
                            Saksnummer(3001),
                        ),
                    )
            },
        ).let {
            it.service.sendPåminnelser() shouldBe SendPåminnelseNyStønadsperiodeContext(
                clock = desemberClock,
                id = NameAndYearMonthId(
                    name = "SendPåminnelseNyStønadsperiode",
                    yearMonth = YearMonth.of(2021, Month.DECEMBER),
                ),
                opprettet = Tidspunkt.now(desemberClock),
                endret = Tidspunkt.now(desemberClock),
                prosessert = setOf(
                    Saksnummer(3000),
                    Saksnummer(3001),
                    Saksnummer(3002),
                    Saksnummer(3003),
                ),
                sendt = setOf(
                    Saksnummer(3000),
                    Saksnummer(3001),
                    Saksnummer(3003),
                ),
            )
        }
    }

    @Test
    fun `utvalg av saker hvor ytelse naturlig avsluttes i inneværende måned`() {
        val juliClock = Clock.fixed(11.juli(2021).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

        // naturlig utløp i forrige måned
        val (sak1, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = Saksnummer(3001),
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 30.juni(2021))),
        )
        // naturlig utløp i inneværende måned
        val (sak2, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = Saksnummer(3002),
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.juli(2021))),
        )
        // naturlig utløp i neste måned
        val (sak3, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = Saksnummer(3003),
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.august(2021))),
        )

        // opphør fra fra neste måned
        val (sak4, _) = vedtakRevurdering(
            saksnummer = Saksnummer(3004),
            stønadsperiode = Stønadsperiode.create(år(2021)),
            revurderingsperiode = Periode.create(1.august(2021), 31.desember(2021)),
            grunnlagsdataOverrides = listOf(
                formueGrunnlagUtenEpsAvslått(
                    periode = Periode.create(1.august(2021), 31.desember(2021)),
                    bosituasjon = bosituasjongrunnlagEnslig(
                        periode = Periode.create(1.august(2021), 31.desember(2021)),
                    ),
                ),
            ),
        )

        // revurdert med naturlig utløp inneværende måned
        val (sak5, _) = vedtakRevurdering(
            saksnummer = Saksnummer(3005),
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.juli(2021))),
            revurderingsperiode = Periode.create(1.mai(2021), 31.juli(2021)),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt1000(
                    periode = Periode.create(1.mai(2021), 31.juli(2021)),
                ),
            ),
        )

        SendPåminnelseNyStønadsperiodeServiceAndMocks(
            clock = juliClock,
            sakRepo = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSaker() } doReturn listOf(
                    SakInfo(sak1.id, sak1.saksnummer, sak1.fnr, sak1.type),
                    SakInfo(sak2.id, sak2.saksnummer, sak2.fnr, sak2.type),
                    SakInfo(sak3.id, sak3.saksnummer, sak3.fnr, sak3.type),
                    SakInfo(sak4.id, sak4.saksnummer, sak4.fnr, sak4.type),
                    SakInfo(sak5.id, sak5.saksnummer, sak5.fnr, sak5.type),
                )
                on { hentSak(any<Saksnummer>()) } doReturnConsecutively listOf(
                    sak1,
                    sak2,
                    sak3,
                    sak4,
                    sak5,
                )
            },
            sessionFactory = TestSessionFactory(),
            brevService = mock {
                on { lagDokument(any<GenererDokumentCommand>()) } doReturnConsecutively listOf(
                    Dokument.UtenMetadata.Informasjon.Viktig(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(juliClock),
                        tittel = PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel(),
                        generertDokument = PdfA("pdf".toByteArray()),
                        generertDokumentJson = "{}",
                    ).right(),
                )
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            sendPåminnelseNyStønadsperiodeJobRepo = mock {
                on { hent(any()) } doReturn null
            },
        ).let {
            it.service.sendPåminnelser() shouldBe SendPåminnelseNyStønadsperiodeContext(
                clock = juliClock,
                id = NameAndYearMonthId(
                    name = "SendPåminnelseNyStønadsperiode",
                    yearMonth = YearMonth.of(2021, Month.JULY),
                ),
                opprettet = Tidspunkt.now(juliClock),
                endret = Tidspunkt.now(juliClock),
                prosessert = setOf(
                    Saksnummer(3001),
                    Saksnummer(3002),
                    Saksnummer(3003),
                    Saksnummer(3004),
                    Saksnummer(3005),
                ),
                sendt = setOf(
                    Saksnummer(3002),
                    Saksnummer(3005),
                ),
            )
        }
    }

    @Test
    fun `gjør ingenting dersom alle saker er prosessert for aktuell måned`() {
        SendPåminnelseNyStønadsperiodeServiceAndMocks(
            clock = fixedClock,
            sakRepo = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSaker() } doReturn emptyList()
            },
            sendPåminnelseNyStønadsperiodeJobRepo = mock {
                on { hent(any()) } doReturn null
            },
        ).let {
            it.service.sendPåminnelser() shouldBe SendPåminnelseNyStønadsperiodeContext(
                clock = fixedClock,
                id = NameAndYearMonthId(
                    name = "SendPåminnelseNyStønadsperiode",
                    yearMonth = YearMonth.of(2021, Month.JANUARY),
                ),
                opprettet = Tidspunkt.now(fixedClock),
                endret = Tidspunkt.now(fixedClock),
                prosessert = emptySet(),
                sendt = emptySet(),
            )

            verify(it.sakRepo).hentSakIdSaksnummerOgFnrForAlleSaker()
            verify(it.sendPåminnelseNyStønadsperiodeJobRepo).hent(
                SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(
                    fixedClock,
                ),
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `prosesserer saker selv om de ikke har noen vedtak enda`() {
        val sak = Sak(
            saksnummer = Saksnummer(3000),
            opprettet = Tidspunkt.now(fixedClock),
            fnr = Fnr.generer(),
            utbetalinger = Utbetalinger(),
            type = Sakstype.UFØRE,
            uteståendeAvkorting = Avkortingsvarsel.Ingen,
            versjon = Hendelsesversjon(1),
            uteståendeKravgrunnlag = null,
        )

        SendPåminnelseNyStønadsperiodeServiceAndMocks(
            clock = fixedClock,
            sakRepo = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSaker() } doReturn listOf(
                    SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type),
                )
                on { hentSak(any<Saksnummer>()) } doReturn sak
            },
            sendPåminnelseNyStønadsperiodeJobRepo = mock {
                on { hent(any()) } doReturn null
            },
        ).let {
            it.service.sendPåminnelser() shouldBe SendPåminnelseNyStønadsperiodeContext(
                clock = fixedClock,
                id = NameAndYearMonthId(
                    name = "SendPåminnelseNyStønadsperiode",
                    yearMonth = YearMonth.of(2021, Month.JANUARY),
                ),
                opprettet = Tidspunkt.now(fixedClock),
                endret = Tidspunkt.now(fixedClock),
                prosessert = setOf(
                    Saksnummer(3000),
                ),
                sendt = emptySet(),
            )
        }
    }

    data class SendPåminnelseNyStønadsperiodeServiceAndMocks(
        val clock: Clock = mock(),
        val sakRepo: SakRepo = mock(),
        val sessionFactory: SessionFactory = TestSessionFactory(),
        val brevService: BrevService = mock(),
        val personService: PersonService = mock(),
        val sendPåminnelseNyStønadsperiodeJobRepo: SendPåminnelseNyStønadsperiodeJobRepo = mock(),
        val formuegrenserFactory: FormuegrenserFactory = formuegrenserFactoryTestPåDato(),
    ) {
        val service = SendPåminnelserOmNyStønadsperiodeServiceImpl(
            clock = clock,
            sakRepo = sakRepo,
            sessionFactory = sessionFactory,
            brevService = brevService,
            sendPåminnelseNyStønadsperiodeJobRepo = sendPåminnelseNyStønadsperiodeJobRepo,
            formuegrenserFactory = formuegrenserFactory,
        )

        fun verifyNoMoreInteractions() {
            org.mockito.kotlin.verifyNoMoreInteractions(
                sakRepo,
                brevService,
                personService,
                sendPåminnelseNyStønadsperiodeJobRepo,
            )
        }
    }
}
