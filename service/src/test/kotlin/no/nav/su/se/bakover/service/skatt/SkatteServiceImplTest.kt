package no.nav.su.se.bakover.service.skatt

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import dokument.domain.journalføring.QueryJournalpostClient
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.toRange
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.skatt.nySamletSkattegrunnlagForÅr
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlagForÅr
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.PersonService
import vilkår.skatt.application.FrioppslagSkattRequest
import vilkår.skatt.application.GenererSkattPdfRequest
import vilkår.skatt.domain.KunneIkkeHenteSkattemelding
import vilkår.skatt.domain.SamletSkattegrunnlagForÅr
import vilkår.skatt.domain.SamletSkattegrunnlagForÅrOgStadie
import vilkår.skatt.domain.Skatteoppslag
import java.time.Clock
import java.time.Year

class SkatteServiceImplTest {
    /**
     * error = error
     * true = har denne tilgjengelig
     * false = har ikke tilgjengelig
     */

    @Test
    fun `spør for et år - {oppgjør - error, utkast - true} - Vi svarer med error`() {
        val mocked = mockedServices(
            mock {
                val år = Year.of(2020)
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagForÅr(
                    utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = år,
                    ),
                    oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = KunneIkkeHenteSkattemelding.Nettverksfeil.left(),
                        inntektsår = år,
                    ),
                    år = år,
                )
            },
        )

        mocked.service.hentSamletSkattegrunnlag(fnr, saksbehandler).let {
            it shouldBe nySkattegrunnlag(
                id = it.id,
                fnr = fnr,
                saksbehandler = saksbehandler,
                årsgrunnlag = nonEmptyListOf(
                    SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = KunneIkkeHenteSkattemelding.Nettverksfeil.left(),
                        inntektsår = Year.of(2020),
                    ),
                ),
            )
        }
    }

    @Test
    fun `spør for et år - {oppgjør - true, utkast - true} - Vi svarer med oppgjør`() {
        val mocked = mockedServices(
            mock {
                val år = Year.of(2020)
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagForÅr(
                    utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = år,
                    ),
                    oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = år,
                    ),
                    år = år,
                )
            },
        )

        mocked.service.hentSamletSkattegrunnlag(fnr, saksbehandler).let {
            it shouldBe nySkattegrunnlag(
                id = it.id,
                fnr = fnr,
                saksbehandler = saksbehandler,
                årsgrunnlag = nonEmptyListOf(
                    SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = Year.of(2020),
                    ),
                ),
                clock = fixedClock,
                hentetTidspunkt = fixedTidspunkt,
                årSpurtFor = Year.of(2020).toRange(),
            )
        }
    }

    @Test
    fun `spør for et år - {oppgjør - false, utkast - error} - Vi svarer med error`() {
        val mocked = mockedServices(
            mock {
                val år = Year.of(2020)
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagForÅr(
                    utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = KunneIkkeHenteSkattemelding.Nettverksfeil.left(),
                        inntektsår = år,
                    ),
                    oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = år,
                    ),
                    år = år,
                )
            },
        )

        mocked.service.hentSamletSkattegrunnlag(fnr, saksbehandler).let {
            it shouldBe nySkattegrunnlag(
                id = it.id,
                fnr = fnr,
                saksbehandler = saksbehandler,
                årsgrunnlag = nonEmptyListOf(
                    SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = KunneIkkeHenteSkattemelding.Nettverksfeil.left(),
                        inntektsår = Year.of(2020),
                    ),
                ),
                clock = fixedClock,
                hentetTidspunkt = fixedTidspunkt,
                årSpurtFor = Year.of(2020).toRange(),
            )
        }
    }

    @Test
    fun `spør for et år - {oppgjør - false, utkast - true} - Vi svarer med utkast`() {
        val mocked = mockedServices(
            mock {
                val år = Year.of(2020)
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagForÅr(
                    utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = år,
                    ),
                    oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = år,
                    ),
                    år = år,
                )
            },
        )

        mocked.service.hentSamletSkattegrunnlag(fnr, saksbehandler).let {
            it shouldBe nySkattegrunnlag(
                id = it.id,
                fnr = fnr,
                saksbehandler = saksbehandler,
                årsgrunnlag = nonEmptyListOf(
                    SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = Year.of(2020),
                    ),
                ),
                clock = fixedClock,
                hentetTidspunkt = fixedTidspunkt,
                årSpurtFor = Year.of(2020).toRange(),
            )
        }
    }

    @Test
    fun `spør for et år - {oppgjør - false, utkast - false} - Vi svarer med fant ingenting`() {
        val år = Year.of(2020)
        val mocked = mockedServices(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagForÅr(
                    utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = år,
                    ),
                    oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = år,
                    ),
                    år = år,
                )
            },
        )

        mocked.service.hentSamletSkattegrunnlag(fnr, saksbehandler).let {
            it shouldBe nySkattegrunnlag(
                id = it.id,
                fnr = fnr,
                saksbehandler = saksbehandler,
                årsgrunnlag = nonEmptyListOf(
                    SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = Year.of(2020),
                    ),
                ),
                clock = fixedClock,
                hentetTidspunkt = fixedTidspunkt,
                årSpurtFor = Year.of(2020).toRange(),
            )
        }
    }

    @Test
    fun `ingen skattedata for periode over 3 år`() {
        val mocked = mockedServices(
            mock {
                on { hentSamletSkattegrunnlagForÅrsperiode(any(), any()) } doReturn nonEmptyListOf(
                    SamletSkattegrunnlagForÅr(
                        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                            oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                            inntektsår = Year.of(2021),
                        ),
                        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                            oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                            inntektsår = Year.of(2021),
                        ),
                        år = Year.of(2021),
                    ),
                    SamletSkattegrunnlagForÅr(
                        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                            oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                            inntektsår = Year.of(2022),
                        ),
                        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                            oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                            inntektsår = Year.of(2022),
                        ),
                        år = Year.of(2022),
                    ),
                    SamletSkattegrunnlagForÅr(
                        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                            oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                            inntektsår = Year.of(2023),
                        ),
                        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                            oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                            inntektsår = Year.of(2023),
                        ),
                        år = Year.of(2023),
                    ),
                )
            },
        )

        mocked.service.hentSamletSkattegrunnlagForÅr(
            fnr,
            saksbehandler,
            YearRange(Year.of(2021), Year.of(2023)),
        ).let {
            it shouldBe nySkattegrunnlag(
                id = it.id,
                fnr = fnr,
                saksbehandler = saksbehandler,
                årsgrunnlag = nonEmptyListOf(
                    SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = Year.of(2021),
                    ),
                    SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = Year.of(2022),
                    ),
                    SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = Year.of(2023),
                    ),
                ),
                clock = fixedClock,
                hentetTidspunkt = fixedTidspunkt,
                årSpurtFor = YearRange(Year.of(2021), Year.of(2023)),
            )
        }
    }

    @Test
    fun `henter skattedata for et år (2021)`() {
        val år = Year.of(2021)
        val mocked = mockedServices(
            mock {
                on { hentSamletSkattegrunnlagForÅrsperiode(any(), any()) } doReturn nonEmptyListOf(
                    SamletSkattegrunnlagForÅr(
                        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = år,
                        ),
                        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = år,
                        ),
                        år = år,
                    ),
                )
            },
        )

        mocked.service.hentSamletSkattegrunnlagForÅr(fnr, saksbehandler, YearRange(år, år)).let {
            it shouldBe nySkattegrunnlag(it.id)
        }
    }

    @Test
    fun `henter skattedata over 3 år (2021-2023)`() {
        val mocked = mockedServices(
            mock {
                on { hentSamletSkattegrunnlagForÅrsperiode(any(), any()) } doReturn nonEmptyListOf(
                    SamletSkattegrunnlagForÅr(
                        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = Year.of(2021),
                        ),
                        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = Year.of(2021),
                        ),
                        år = Year.of(2021),
                    ),
                    SamletSkattegrunnlagForÅr(
                        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = Year.of(2022),
                        ),
                        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = Year.of(2022),
                        ),
                        år = Year.of(2022),
                    ),
                    SamletSkattegrunnlagForÅr(
                        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = Year.of(2023),
                        ),
                        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = Year.of(2023),
                        ),
                        år = Year.of(2023),
                    ),
                )
            },
        )
        mocked.service.hentSamletSkattegrunnlagForÅr(
            fnr,
            saksbehandler,
            YearRange(Year.of(2021), Year.of(2023)),
        ).let {
            it shouldBe nySkattegrunnlag(
                id = it.id,
                fnr = fnr,
                saksbehandler = saksbehandler,
                årsgrunnlag = nonEmptyListOf(
                    SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = Year.of(2021),
                    ),
                    SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = Year.of(2022),
                    ),
                    SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = Year.of(2023),
                    ),
                ),
                clock = fixedClock,
                hentetTidspunkt = fixedTidspunkt,
                årSpurtFor = YearRange(Year.of(2021), Year.of(2023)),
            )
        }
    }

    @Test
    fun `henter skattegrunnlag og lager pdf av den for forhåndsvisning`() {
        val samletSkattegrunnlag = nySamletSkattegrunnlagForÅr()

        val skatteClient = mock<Skatteoppslag> {
            on { hentSamletSkattegrunnlag(any(), any()) } doReturn samletSkattegrunnlag
        }

        val skattDokumentService = mock<SkattDokumentService> {
            on { genererSkattePdf(any()) } doReturn PdfA("content".toByteArray()).right()
        }

        mockedServices(
            skatteClient = skatteClient,
            skattDokumentService = skattDokumentService,
        ).let {
            it.service.hentOgLagSkattePdf(
                request = FrioppslagSkattRequest(
                    fnr = fnr,
                    epsFnr = null,
                    år = Year.of(2021),
                    begrunnelse = "begrunnelse for henting av skatte-data",
                    saksbehandler = saksbehandler,
                    sakstype = Sakstype.ALDER,
                    fagsystemId = "29901",
                ),
            ).shouldBeRight()

            verify(it.skatteClient).hentSamletSkattegrunnlag(
                argThat { it shouldBe fnr },
                argThat { it shouldBe Year.of(2021) },
            )
            verify(it.skattDokumentService).genererSkattePdf(
                argThat {
                    it shouldBe GenererSkattPdfRequest(
                        skattegrunnlagSøkers = it.skattegrunnlagSøkers,
                        skattegrunnlagEps = null,
                        begrunnelse = "begrunnelse for henting av skatte-data",
                        sakstype = Sakstype.ALDER,
                        fagsystemId = "29901",
                    )
                },
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `henter skattegrunnlag og lager pdf av den for journalføring for uføresak`() {
        val samletSkattegrunnlag = nySamletSkattegrunnlagForÅr()
        val person = person()
        val sak = nySakUføre().first

        val sakService = mock<SakService> {
            on { hentSak(any<Saksnummer>()) } doReturn sak.right()
        }
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(any()) } doReturn Unit.right()
            on { hentPerson(any()) } doReturn person.right()
        }

        val skatteClient = mock<Skatteoppslag> {
            on { hentSamletSkattegrunnlag(any(), any()) } doReturn samletSkattegrunnlag
        }

        val skattDokumentService = mock<SkattDokumentService> {
            on { genererSkattePdfOgJournalfør(any()) } doReturn PdfA("content".toByteArray()).right()
        }

        mockedServices(
            skatteClient = skatteClient,
            skattDokumentService = skattDokumentService,
            personService = personService,
            sakService = sakService,
        ).let {
            it.service.hentLagOgJournalførSkattePdf(
                request = FrioppslagSkattRequest(
                    fnr = fnr,
                    epsFnr = null,
                    år = Year.of(2021),
                    begrunnelse = "begrunnelse for henting av skatte-data",
                    saksbehandler = saksbehandler,
                    sakstype = Sakstype.UFØRE,
                    fagsystemId = sak.saksnummer.toString(),
                ),
            ).shouldBeRight()

            verify(it.sakService).hentSak(argThat<Saksnummer> { it shouldBe sak.saksnummer })
            verify(it.personService).hentPerson(argThat { it shouldBe fnr })

            verify(it.skatteClient).hentSamletSkattegrunnlag(
                argThat { it shouldBe fnr },
                argThat { it shouldBe Year.of(2021) },
            )
            verify(it.skattDokumentService).genererSkattePdfOgJournalfør(
                argThat {
                    it shouldBe GenererSkattPdfRequest(
                        skattegrunnlagSøkers = it.skattegrunnlagSøkers,
                        skattegrunnlagEps = null,
                        begrunnelse = "begrunnelse for henting av skatte-data",
                        sakstype = Sakstype.UFØRE,
                        fagsystemId = sak.saksnummer.toString(),
                    )
                },
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `henter skattegrunnlag og lager pdf for journalføring for aldersak`() {
        val fagsystemId = "1279CB56"
        val samletSkattegrunnlag = nySamletSkattegrunnlagForÅr()
        val person = person()

        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(any()) } doReturn Unit.right()
            on { hentPerson(any()) } doReturn person.right()
        }

        val journalpostClient = mock<QueryJournalpostClient> {
            on { finnesFagsak(any(), any(), anyOrNull()) } doReturn true.right()
        }

        val skatteClient = mock<Skatteoppslag> {
            on { hentSamletSkattegrunnlag(any(), any()) } doReturn samletSkattegrunnlag
        }

        val skattDokumentService = mock<SkattDokumentService> {
            on { genererSkattePdfOgJournalfør(any()) } doReturn PdfA("content".toByteArray()).right()
        }

        mockedServices(
            skatteClient = skatteClient,
            skattDokumentService = skattDokumentService,
            personService = personService,
            journalpostClient = journalpostClient,
        ).let {
            it.service.hentLagOgJournalførSkattePdf(
                request = FrioppslagSkattRequest(
                    fnr = fnr,
                    epsFnr = null,
                    år = Year.of(2021),
                    begrunnelse = "begrunnelse for henting av skatte-data",
                    saksbehandler = saksbehandler,
                    sakstype = Sakstype.ALDER,
                    fagsystemId = fagsystemId,
                ),
            ).shouldBeRight()

            verify(it.personService).hentPerson(argThat { it shouldBe fnr })
            verify(it.journalpostClient).finnesFagsak(argThat { it shouldBe fnr }, argThat { it shouldBe fagsystemId }, anyOrNull())
            verify(it.skatteClient).hentSamletSkattegrunnlag(
                argThat { it shouldBe fnr },
                argThat { it shouldBe Year.of(2021) },
            )
            verify(it.skattDokumentService).genererSkattePdfOgJournalfør(
                argThat {
                    it shouldBe GenererSkattPdfRequest(
                        skattegrunnlagSøkers = it.skattegrunnlagSøkers,
                        skattegrunnlagEps = null,
                        begrunnelse = "begrunnelse for henting av skatte-data",
                        sakstype = Sakstype.ALDER,
                        fagsystemId = fagsystemId,
                    )
                },
            )

            it.verifyNoMoreInteractions()
        }
    }

    private data class mockedServices(
        val skatteClient: Skatteoppslag = mock(),
        val skattDokumentService: SkattDokumentService = mock(),
        val personService: PersonService = mock(),
        val sakService: SakService = mock(),
        val journalpostClient: QueryJournalpostClient = mock(),
        val clock: Clock = fixedClock,
    ) {
        val service = SkatteServiceImpl(
            skatteClient = skatteClient,
            skattDokumentService = skattDokumentService,
            personService = personService,
            sakService = sakService,
            journalpostClient = journalpostClient,
            isProd = false,
            clock = fixedClock,
        )

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(skatteClient, skattDokumentService, personService, sakService, journalpostClient)
        }
    }
}
