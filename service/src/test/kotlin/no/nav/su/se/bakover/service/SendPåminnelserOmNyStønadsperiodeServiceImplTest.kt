package no.nav.su.se.bakover.service

import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.GenererDokumentCommand
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.job.NameAndYearMonthId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.command.PåminnelseNyStønadsperiodeDokumentCommand
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.domain.sak.FantIkkeSak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.stønadsperiode.SendPåminnelseNyStønadsperiodeJobRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import person.domain.PersonService
import vilkår.formue.domain.FormuegrenserFactory
import økonomi.domain.utbetaling.Utbetalinger
import java.time.Clock
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import org.mockito.kotlin.argThat as mockitoArgThat

internal class SendPåminnelserOmNyStønadsperiodeServiceImplTest {
    @Test
    fun `hopper over saker som feiler og lagrer saker som er ok`() {
        val desemberClock =
            Clock.fixed(11.desember(2021).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val jobbmåned = YearMonth.of(2021, Month.DECEMBER)
        val månedPåminnelsenGjelder = jobbmåned.plusMonths(1)

        val (sakDerDokumentgenereringFeiler, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = Saksnummer(3000),
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2022), 31.januar(2022))),
        )
        val (sakSomFårPåminnelse, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = Saksnummer(3001),
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2022), 31.januar(2022))),
        )
        val sakInfoSomIkkeFinnesVedDetaljhenting = SakInfo(
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(3002),
            fnr = Fnr.generer(),
            type = Sakstype.UFØRE,
        )

        SendPåminnelseNyStønadsperiodeServiceAndMocks(
            clock = desemberClock,
            sakService = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst() } doReturn listOf(
                    sakDerDokumentgenereringFeiler.tilSakInfo(),
                    sakSomFårPåminnelse.tilSakInfo(),
                    sakInfoSomIkkeFinnesVedDetaljhenting,
                )
                on { hentSak(sakDerDokumentgenereringFeiler.saksnummer) } doReturn sakDerDokumentgenereringFeiler.right()
                on { hentSak(sakSomFårPåminnelse.saksnummer) } doReturn sakSomFårPåminnelse.right()
                on { hentSak(sakInfoSomIkkeFinnesVedDetaljhenting.saksnummer) } doReturn FantIkkeSak.left()
            },
            sessionFactory = TestSessionFactory(),
            brevService = mock {
                on {
                    lagDokumentPdf(
                        mockitoArgThat { saksnummer == sakDerDokumentgenereringFeiler.saksnummer },
                        anyOrNull(),
                    )
                } doReturn KunneIkkeLageDokument.FeilVedGenereringAvPdf.left()
                on {
                    lagDokumentPdf(
                        mockitoArgThat { saksnummer == sakSomFårPåminnelse.saksnummer },
                        anyOrNull(),
                    )
                } doReturn
                    Dokument.UtenMetadata.Informasjon.Viktig(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(desemberClock),
                        tittel = PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel(),
                        generertDokument = PdfA("pdf".toByteArray()),
                        generertDokumentJson = "{}",
                    ).right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any(), any()) } doReturn person().right()
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
                    yearMonth = jobbmåned,
                ),
                opprettet = Tidspunkt.now(desemberClock),
                endret = Tidspunkt.now(desemberClock),
                prosessert = setOf(
                    sakSomFårPåminnelse.saksnummer,
                ),
                sendt = setOf(
                    sakSomFårPåminnelse.saksnummer,
                ),
                feilet = listOf(
                    SendPåminnelseNyStønadsperiodeContext.Feilet(
                        sakDerDokumentgenereringFeiler.saksnummer,
                        SendPåminnelseNyStønadsperiodeContext.KunneIkkeSendePåminnelse.KunneIkkeLageBrev.toString(),
                    ),
                    SendPåminnelseNyStønadsperiodeContext.Feilet(
                        sakInfoSomIkkeFinnesVedDetaljhenting.saksnummer,
                        NullPointerException().toString(),
                    ),
                ),
            )

            serviceAndMocks.service.sendPåminnelser() shouldBe expectedContext

            val captor = argumentCaptor<GenererDokumentCommand>()
            verify(serviceAndMocks.brevService, times(2)).lagDokumentPdf(captor.capture(), anyOrNull())

            captor.lastValue shouldBe PåminnelseNyStønadsperiodeDokumentCommand(
                saksnummer = sakSomFårPåminnelse.saksnummer,
                sakstype = Sakstype.UFØRE,
                utløpsdato = LocalDate.of(månedPåminnelsenGjelder.year, månedPåminnelsenGjelder.month, 31),
                fødselsnummer = sakSomFårPåminnelse.fnr,
                uføreSomFyller67 = false,
            )

            verify(serviceAndMocks.brevService).lagreDokument(
                dokument = argThat {
                    it.tittel shouldBe PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel()
                    it.metadata shouldBe Dokument.Metadata(
                        sakId = sakSomFårPåminnelse.id,
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
        val jobbmåned = YearMonth.of(2021, Month.DECEMBER)
        val eksisterendeProsesserte = setOf(Saksnummer(3000), Saksnummer(3001), Saksnummer(3002))
        val eksisterendeSendte = setOf(Saksnummer(3000), Saksnummer(3001))

        val (nySakSomFårPåminnelse, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = Saksnummer(3003),
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2022), 31.januar(2022))),
        )

        SendPåminnelseNyStønadsperiodeServiceAndMocks(
            clock = desemberClock,
            sakService = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst() } doReturn listOf(
                    nySakSomFårPåminnelse.tilSakInfo(),
                )
                on { hentSak(nySakSomFårPåminnelse.saksnummer) } doReturn nySakSomFårPåminnelse.right()
            },
            sessionFactory = TestSessionFactory(),
            brevService = mock {
                on {
                    lagDokumentPdf(
                        mockitoArgThat { saksnummer == nySakSomFårPåminnelse.saksnummer },
                        anyOrNull(),
                    )
                } doReturn
                    Dokument.UtenMetadata.Informasjon.Viktig(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(desemberClock),
                        tittel = PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel(),
                        generertDokument = PdfA("pdf".toByteArray()),
                        generertDokumentJson = "{}",
                    ).right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any(), any()) } doReturn person().right()
            },
            sendPåminnelseNyStønadsperiodeJobRepo = mock {
                on { hent(any()) } doReturn
                    SendPåminnelseNyStønadsperiodeContext(
                        clock = desemberClock,
                        id = NameAndYearMonthId(
                            name = "SendPåminnelseNyStønadsperiode",
                            yearMonth = jobbmåned,
                        ),
                        opprettet = Tidspunkt.now(desemberClock),
                        endret = Tidspunkt.now(desemberClock),
                        prosessert = eksisterendeProsesserte,
                        sendt = eksisterendeSendte,
                    )
            },
        ).let {
            it.service.sendPåminnelser() shouldBe SendPåminnelseNyStønadsperiodeContext(
                clock = desemberClock,
                id = NameAndYearMonthId(
                    name = "SendPåminnelseNyStønadsperiode",
                    yearMonth = jobbmåned,
                ),
                opprettet = Tidspunkt.now(desemberClock),
                endret = Tidspunkt.now(desemberClock),
                prosessert = eksisterendeProsesserte + nySakSomFårPåminnelse.saksnummer,
                sendt = eksisterendeSendte + nySakSomFårPåminnelse.saksnummer,
            )
        }
    }

    @Test
    fun `utvalg av saker hvor ytelse naturlig avsluttes neste måned`() {
        val juliClock = Clock.fixed(11.juli(2021).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val jobbmåned = YearMonth.of(2021, Month.JULY)

        val (sakUtløperForrigeMåned, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = Saksnummer(3001),
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 30.juni(2021))),
        )
        val (sakUtløperIJobbmåned, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = Saksnummer(3002),
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.juli(2021))),
        )
        val (sakUtløperMånedenEtter, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = Saksnummer(3003),
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.august(2021))),
        )

        val (sakOpphørerMånedenEtter, _) = vedtakRevurdering(
            saksnummer = Saksnummer(3004),
            stønadsperiode = Stønadsperiode.create(år(2021)),
            revurderingsperiode = Periode.create(1.august(2021), 31.desember(2021)),
            grunnlagsdataOverrides = listOf(
                formueGrunnlagUtenEpsAvslått(
                    periode = Periode.create(1.august(2021), 31.desember(2021)),
                ),
            ),
        )

        val (revurdertSakUtløperIJobbmåned, _) = vedtakRevurdering(
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
            sakService = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst() } doReturn listOf(
                    sakUtløperForrigeMåned.tilSakInfo(),
                    sakUtløperIJobbmåned.tilSakInfo(),
                    sakUtløperMånedenEtter.tilSakInfo(),
                    sakOpphørerMånedenEtter.tilSakInfo(),
                    revurdertSakUtløperIJobbmåned.tilSakInfo(),
                )
                on { hentSak(sakUtløperForrigeMåned.saksnummer) } doReturn sakUtløperForrigeMåned.right()
                on { hentSak(sakUtløperIJobbmåned.saksnummer) } doReturn sakUtløperIJobbmåned.right()
                on { hentSak(sakUtløperMånedenEtter.saksnummer) } doReturn sakUtløperMånedenEtter.right()
                on { hentSak(sakOpphørerMånedenEtter.saksnummer) } doReturn sakOpphørerMånedenEtter.right()
                on { hentSak(revurdertSakUtløperIJobbmåned.saksnummer) } doReturn revurdertSakUtløperIJobbmåned.right()
            },
            sessionFactory = TestSessionFactory(),
            brevService = mock {
                on {
                    lagDokumentPdf(
                        mockitoArgThat { saksnummer == sakUtløperMånedenEtter.saksnummer },
                        anyOrNull(),
                    )
                } doReturn
                    Dokument.UtenMetadata.Informasjon.Viktig(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(juliClock),
                        tittel = PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel(),
                        generertDokument = PdfA("pdf".toByteArray()),
                        generertDokumentJson = "{}",
                    ).right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any(), any()) } doReturn person().right()
            },
            sendPåminnelseNyStønadsperiodeJobRepo = mock {
                on { hent(any()) } doReturn null
            },
        ).let {
            it.service.sendPåminnelser() shouldBe SendPåminnelseNyStønadsperiodeContext(
                clock = juliClock,
                id = NameAndYearMonthId(
                    name = "SendPåminnelseNyStønadsperiode",
                    yearMonth = jobbmåned,
                ),
                opprettet = Tidspunkt.now(juliClock),
                endret = Tidspunkt.now(juliClock),
                prosessert = setOf(
                    sakUtløperForrigeMåned.saksnummer,
                    sakUtløperIJobbmåned.saksnummer,
                    sakUtløperMånedenEtter.saksnummer,
                    sakOpphørerMånedenEtter.saksnummer,
                    revurdertSakUtløperIJobbmåned.saksnummer,
                ),
                sendt = setOf(
                    sakUtløperMånedenEtter.saksnummer,
                ),
            )
        }
    }

    @Test
    fun `prosesserer men sender ikke påminnelse dersom saken opphører måneden etter jobbmåned`() {
        val juliClock = Clock.fixed(11.juli(2021).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val jobbmåned = YearMonth.of(2021, Month.JULY)

        val (sakOpphørerMånedenEtter, _) = vedtakRevurdering(
            saksnummer = Saksnummer(3004),
            stønadsperiode = Stønadsperiode.create(år(2021)),
            revurderingsperiode = Periode.create(1.august(2021), 31.desember(2021)),
            grunnlagsdataOverrides = listOf(
                formueGrunnlagUtenEpsAvslått(
                    periode = Periode.create(1.august(2021), 31.desember(2021)),
                ),
            ),
        )

        SendPåminnelseNyStønadsperiodeServiceAndMocks(
            clock = juliClock,
            sakService = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst() } doReturn listOf(
                    sakOpphørerMånedenEtter.tilSakInfo(),
                )
                on { hentSak(any<Saksnummer>()) } doReturn sakOpphørerMånedenEtter.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any(), any()) } doReturn person().right()
            },
            sendPåminnelseNyStønadsperiodeJobRepo = mock {
                on { hent(any()) } doReturn null
            },
        ).let {
            it.service.sendPåminnelser() shouldBe SendPåminnelseNyStønadsperiodeContext(
                clock = juliClock,
                id = NameAndYearMonthId(
                    name = "SendPåminnelseNyStønadsperiode",
                    yearMonth = jobbmåned,
                ),
                opprettet = Tidspunkt.now(juliClock),
                endret = Tidspunkt.now(juliClock),
                prosessert = setOf(
                    sakOpphørerMånedenEtter.saksnummer,
                ),
                sendt = emptySet(),
            )
            verify(it.brevService, times(0)).lagDokumentPdf(any<GenererDokumentCommand>(), anyOrNull())
        }
    }

    @Test
    fun `gjør ingenting dersom alle saker er prosessert for aktuell måned`() {
        SendPåminnelseNyStønadsperiodeServiceAndMocks(
            clock = fixedClock,
            sakService = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst() } doReturn emptyList()
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

            verify(it.sakService).hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst()
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
        val sakUtenVedtak = Sak(
            saksnummer = Saksnummer(3000),
            opprettet = Tidspunkt.now(fixedClock),
            fnr = Fnr.generer(),
            utbetalinger = Utbetalinger(),
            type = Sakstype.UFØRE,
            versjon = Hendelsesversjon(1),
            uteståendeKravgrunnlag = null,
        )

        SendPåminnelseNyStønadsperiodeServiceAndMocks(
            clock = fixedClock,
            sakService = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst() } doReturn listOf(
                    sakUtenVedtak.tilSakInfo(),
                )
                on { hentSak(any<Saksnummer>()) } doReturn sakUtenVedtak.right()
            },
            sendPåminnelseNyStønadsperiodeJobRepo = mock {
                on { hent(any()) } doReturn null
            },
            personService = mock {
                on { hentPersonMedSystembruker(any(), any()) } doReturn person(fnr = sakUtenVedtak.fnr).right()
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
                    sakUtenVedtak.saksnummer,
                ),
                sendt = emptySet(),
            )
        }
    }

    data class SendPåminnelseNyStønadsperiodeServiceAndMocks(
        val clock: Clock = mock(),
        val sakService: SakService = mock(),
        val sessionFactory: SessionFactory = TestSessionFactory(),
        val brevService: BrevService = mock(),
        val personService: PersonService = mock(),
        val sendPåminnelseNyStønadsperiodeJobRepo: SendPåminnelseNyStønadsperiodeJobRepo = mock(),
        val formuegrenserFactory: FormuegrenserFactory = formuegrenserFactoryTestPåDato(),
    ) {
        val service = SendPåminnelserOmNyStønadsperiodeServiceImpl(
            clock = clock,
            sakService = sakService,
            sessionFactory = sessionFactory,
            brevService = brevService,
            sendPåminnelseNyStønadsperiodeJobRepo = sendPåminnelseNyStønadsperiodeJobRepo,
            personService = personService,
        )

        fun verifyNoMoreInteractions() {
            org.mockito.kotlin.verifyNoMoreInteractions(
                sakService,
                brevService,
                personService,
                sendPåminnelseNyStønadsperiodeJobRepo,
                personService,
            )
        }
    }
}

private fun Sak.tilSakInfo(): SakInfo = SakInfo(id, saksnummer, fnr, type)
