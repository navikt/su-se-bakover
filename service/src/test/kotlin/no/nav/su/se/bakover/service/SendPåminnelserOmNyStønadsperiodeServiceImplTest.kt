package no.nav.su.se.bakover.service

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.JobContextRepo
import no.nav.su.se.bakover.domain.NameAndYearMonthId
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.sak.SakIdSaksnummerFnr
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.formueGrunnlagUtenEpsAvslått
import no.nav.su.se.bakover.test.formuegrenserFactoryTest
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.generer
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

        SendPåminnelseNyStønadsperiodeServiceAndMocks(
            clock = desemberClock,
            sakRepo = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSaker() } doReturn listOf(
                    SakIdSaksnummerFnr(sak1.id, sak1.saksnummer, sak1.fnr),
                    SakIdSaksnummerFnr(sak2.id, sak2.saksnummer, sak2.fnr),
                )
                on { hentSak(any<Saksnummer>()) } doReturnConsecutively listOf(
                    sak1, sak2,
                )
            },
            sessionFactory = TestSessionFactory(),
            brevService = mock {
                on { lagDokument(any<LagBrevRequest>()) } doReturnConsecutively listOf(
                    KunneIkkeLageDokument.KunneIkkeGenererePDF.left(),
                    Dokument.UtenMetadata.Informasjon(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(desemberClock),
                        tittel = BrevTemplate.PåminnelseNyStønadsperiode.tittel(),
                        generertDokument = "pdf".toByteArray(),
                        generertDokumentJson = "{}",
                    ).right(),
                )
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            jobContextRepo = mock {
                on { hent<SendPåminnelseNyStønadsperiodeContext>(any()) } doReturn null
            },
            formuegrenserFactory = formuegrenserFactoryTest,
        ).let { serviceAndMocks ->
            val expectedContext = SendPåminnelseNyStønadsperiodeContext(
                clock = desemberClock,
                id = NameAndYearMonthId(
                    jobName = "SendPåminnelseNyStønadsperiode",
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
            )

            serviceAndMocks.service.sendPåminnelser() shouldBe expectedContext

            val captor = argumentCaptor<LagBrevRequest>()
            verify(serviceAndMocks.brevService, times(2)).lagDokument(captor.capture())

            captor.lastValue shouldBe LagBrevRequest.PåminnelseNyStønadsperiode(
                person = person(),
                dagensDato = LocalDate.of(2021, Month.DECEMBER, 11),
                saksnummer = Saksnummer(3001),
                utløpsdato = LocalDate.of(2021, Month.DECEMBER, 31),
                halvtGrunnbeløp = 50676,
            )

            verify(serviceAndMocks.brevService).lagreDokument(
                dokument = argThat {
                    it.tittel shouldBe BrevTemplate.PåminnelseNyStønadsperiode.tittel()
                    it.metadata shouldBe Dokument.Metadata(
                        sakId = sak1.id,
                        bestillBrev = true,
                    )
                },
                transactionContext = argThat { it shouldBe serviceAndMocks.sessionFactory.newTransactionContext() },
            )
            verify(serviceAndMocks.jobContextRepo).hent<SendPåminnelseNyStønadsperiodeContext>(
                SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(
                    desemberClock,
                ),
            )
            verify(serviceAndMocks.jobContextRepo).lagre(
                jobContext = argThat { it shouldBe expectedContext },
                transactionContext = argThat { it shouldBe serviceAndMocks.sessionFactory.newTransactionContext() },
            )
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
                    SakIdSaksnummerFnr(sak1.id, sak1.saksnummer, sak1.fnr),
                )
                on { hentSak(any<Saksnummer>()) } doReturnConsecutively listOf(
                    sak1,
                )
            },
            sessionFactory = TestSessionFactory(),
            brevService = mock {
                on { lagDokument(any<LagBrevRequest>()) } doReturnConsecutively listOf(
                    Dokument.UtenMetadata.Informasjon(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(desemberClock),
                        tittel = BrevTemplate.PåminnelseNyStønadsperiode.tittel(),
                        generertDokument = "pdf".toByteArray(),
                        generertDokumentJson = "{}",
                    ).right(),
                )
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            jobContextRepo = mock {
                on { hent<SendPåminnelseNyStønadsperiodeContext>(any()) } doReturn
                    SendPåminnelseNyStønadsperiodeContext(
                        clock = desemberClock,
                        id = NameAndYearMonthId(
                            jobName = "SendPåminnelseNyStønadsperiode",
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
                    jobName = "SendPåminnelseNyStønadsperiode",
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
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 30.juni(2021))),
            saksnummer = Saksnummer(3001),
        )
        // naturlig utløp i inneværende måned
        val (sak2, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.juli(2021))),
            saksnummer = Saksnummer(3002),
        )
        // naturlig utløp i neste måned
        val (sak3, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.august(2021))),
            saksnummer = Saksnummer(3003),
        )

        // opphør fra fra neste måned
        val (sak4, _) = vedtakRevurdering(
            stønadsperiode = Stønadsperiode.create(år(2021)),
            revurderingsperiode = Periode.create(1.august(2021), 31.desember(2021)),
            saksnummer = Saksnummer(3004),
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
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.juli(2021))),
            revurderingsperiode = Periode.create(1.mai(2021), 31.juli(2021)),
            saksnummer = Saksnummer(3005),
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
                    SakIdSaksnummerFnr(sak1.id, sak1.saksnummer, sak1.fnr),
                    SakIdSaksnummerFnr(sak2.id, sak2.saksnummer, sak2.fnr),
                    SakIdSaksnummerFnr(sak3.id, sak3.saksnummer, sak3.fnr),
                    SakIdSaksnummerFnr(sak4.id, sak4.saksnummer, sak4.fnr),
                    SakIdSaksnummerFnr(sak5.id, sak5.saksnummer, sak5.fnr),
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
                on { lagDokument(any<LagBrevRequest>()) } doReturnConsecutively listOf(
                    Dokument.UtenMetadata.Informasjon(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(juliClock),
                        tittel = BrevTemplate.PåminnelseNyStønadsperiode.tittel(),
                        generertDokument = "pdf".toByteArray(),
                        generertDokumentJson = "{}",
                    ).right(),
                )
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            jobContextRepo = mock {
                on { hent<SendPåminnelseNyStønadsperiodeContext>(any()) } doReturn null
            },
        ).let {
            it.service.sendPåminnelser() shouldBe SendPåminnelseNyStønadsperiodeContext(
                clock = juliClock,
                id = NameAndYearMonthId(
                    jobName = "SendPåminnelseNyStønadsperiode",
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
            jobContextRepo = mock {
                on { hent<SendPåminnelseNyStønadsperiodeContext>(any()) } doReturn null
            },
        ).let {
            it.service.sendPåminnelser() shouldBe SendPåminnelseNyStønadsperiodeContext(
                clock = fixedClock,
                id = NameAndYearMonthId(
                    jobName = "SendPåminnelseNyStønadsperiode",
                    yearMonth = YearMonth.of(2021, Month.JANUARY),
                ),
                opprettet = Tidspunkt.now(fixedClock),
                endret = Tidspunkt.now(fixedClock),
                prosessert = emptySet(),
                sendt = emptySet(),
            )

            verify(it.sakRepo).hentSakIdSaksnummerOgFnrForAlleSaker()
            verify(it.jobContextRepo).hent<SendPåminnelseNyStønadsperiodeContext>(
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
            utbetalinger = emptyList(),
        )

        SendPåminnelseNyStønadsperiodeServiceAndMocks(
            clock = fixedClock,
            sakRepo = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSaker() } doReturn listOf(
                    SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr),
                )
                on { hentSak(any<Saksnummer>()) } doReturn sak
            },
            jobContextRepo = mock {
                on { hent<SendPåminnelseNyStønadsperiodeContext>(any()) } doReturn null
            },
        ).let {
            it.service.sendPåminnelser() shouldBe SendPåminnelseNyStønadsperiodeContext(
                clock = fixedClock,
                id = NameAndYearMonthId(
                    jobName = "SendPåminnelseNyStønadsperiode",
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
        val jobContextRepo: JobContextRepo = mock(),
        val formuegrenserFactory: FormuegrenserFactory = formuegrenserFactoryTest,
    ) {
        val service = SendPåminnelserOmNyStønadsperiodeServiceImpl(
            clock = clock,
            sakRepo = sakRepo,
            sessionFactory = sessionFactory,
            brevService = brevService,
            personService = personService,
            jobContextRepo = jobContextRepo,
            formuegrenserFactory = formuegrenserFactory,
        )

        fun verifyNoMoreInteractions() {
            org.mockito.kotlin.verifyNoMoreInteractions(
                sakRepo,
                brevService,
                personService,
                jobContextRepo,
            )
        }
    }
}
