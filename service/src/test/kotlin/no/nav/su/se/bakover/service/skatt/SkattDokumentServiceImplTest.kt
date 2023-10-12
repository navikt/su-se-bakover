package no.nav.su.se.bakover.service.skatt

import arrow.core.nonEmptyListOf
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.pdf.PdfInnhold
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagsPdfInnhold
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagsPdfInnhold.Companion.lagSkattegrunnlagsPdfInnholdFraFrioppslag
import no.nav.su.se.bakover.client.pdf.ÅrsgrunnlagForPdf
import no.nav.su.se.bakover.client.pdf.ÅrsgrunnlagMedFnr
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.trimWhitespace
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.journalpost.JournalpostSkattUtenforSak
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.bosituasjonEpsUnder67
import no.nav.su.se.bakover.test.eksterneGrunnlag.eksternGrunnlagHentet
import no.nav.su.se.bakover.test.eksterneGrunnlag.nyEksternGrunnlagHentetFeil
import no.nav.su.se.bakover.test.epsFnr
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.pdfATom
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.skatt.nySamletSkattegrunnlagForÅrOgStadieOppgjør
import no.nav.su.se.bakover.test.skatt.nySamletSkattegrunnlagForÅrOgStadieOppgjørMedFeilIÅrsgrunnlag
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlagMedFeilIÅrsgrunnlag
import no.nav.su.se.bakover.test.vilkår.formuevilkårMedEps0Innvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.PersonOppslag
import java.time.Clock

internal class SkattDokumentServiceImplTest {

    @Test
    fun `lager skattemeldingspdf for søker uten eps`() {
        val vedtak = iverksattSøknadsbehandling().third
        val person = person()
        val personMock = mock<PersonOppslag> { on { this.person(any()) } doReturn person.right() }
        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<PdfInnhold>()) } doReturn pdfATom().right()
        }
        val dokumentSkatt = mock<DokumentSkattRepo> {}

        val service = mockedServices(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            dokumentSkattRepo = dokumentSkatt,
            journalførSkattDokumentService = mock(),
            clock = fixedClock,
        ).service
        val tx = TestSessionFactory.transactionContext
        val dokument = service.genererOgLagre(vedtak, tx)

        dokument.shouldBeRight()
        verify(personMock).person(argThat { it shouldBe vedtak.fnr })
        verify(pdfGeneratorMock).genererPdf(
            argThat<PdfInnhold> {
                it shouldBe SkattegrunnlagsPdfInnhold.lagSkattegrunnlagsPdf(
                    saksnummer = vedtak.saksnummer,
                    søknadsbehandlingsId = vedtak.behandling.id,
                    vedtaksId = vedtak.id,
                    hentet = fixedTidspunkt,
                    skatt = ÅrsgrunnlagForPdf(
                        søkers = ÅrsgrunnlagMedFnr(
                            fnr = person.ident.fnr,
                            årsgrunnlag = nonEmptyListOf(nySamletSkattegrunnlagForÅrOgStadieOppgjør()),
                        ),
                        eps = null,
                    ),
                    hentNavn = { _ -> person.navn },
                    clock = fixedClock,
                )
            },
        )
        verify(dokumentSkatt).lagre(argThat { it shouldBe dokument.value }, argThat { it shouldBe tx })
        verifyNoMoreInteractions(personMock, pdfGeneratorMock, dokumentSkatt)
    }

    @Test
    fun `lager skattemelding for søker med eps`() {
        val person = person()
        val bosituasjon = bosituasjonEpsUnder67()
        val eps = person(fnr = bosituasjon.fnr)
        val vedtak = iverksattSøknadsbehandling(
            customVilkår = listOf(formuevilkårMedEps0Innvilget(bosituasjon = nonEmptyListOf(bosituasjon))),
            customGrunnlag = listOf(bosituasjon),
            eksterneGrunnlag = eksternGrunnlagHentet().copy(
                skatt = EksterneGrunnlagSkatt.Hentet(nySkattegrunnlag(), nySkattegrunnlag(fnr = bosituasjon.fnr)),
            ),
        ).third
        val personMock = mock<PersonOppslag> { on { this.person(any()) }.thenReturn(person.right(), eps.right()) }
        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<PdfInnhold>()) } doReturn pdfATom().right()
        }
        val dokumentSkatt = mock<DokumentSkattRepo> {}

        val service = mockedServices(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            dokumentSkattRepo = dokumentSkatt,
            journalførSkattDokumentService = mock(),
            clock = fixedClock,
        ).service
        val tx = TestSessionFactory.transactionContext
        val dokument = service.genererOgLagre(vedtak, tx)
        dokument.shouldBeRight()

        val captor = argumentCaptor<Fnr>()
        verify(personMock, times(2)).person(captor.capture())
        captor.allValues.size shouldBe 2
        captor.firstValue shouldBe person.ident.fnr
        captor.lastValue shouldBe eps.ident.fnr
        verify(pdfGeneratorMock).genererPdf(
            argThat<PdfInnhold> {
                it shouldBe SkattegrunnlagsPdfInnhold.lagSkattegrunnlagsPdf(
                    saksnummer = vedtak.saksnummer,
                    søknadsbehandlingsId = vedtak.behandling.id,
                    vedtaksId = vedtak.id,
                    hentet = fixedTidspunkt,
                    skatt = ÅrsgrunnlagForPdf(
                        søkers = ÅrsgrunnlagMedFnr(
                            fnr = person.ident.fnr,
                            årsgrunnlag = nonEmptyListOf(nySamletSkattegrunnlagForÅrOgStadieOppgjør()),
                        ),
                        eps = ÅrsgrunnlagMedFnr(
                            fnr = bosituasjon.fnr,
                            årsgrunnlag = nonEmptyListOf(nySamletSkattegrunnlagForÅrOgStadieOppgjør()),
                        ),
                    ),
                    hentNavn = { _ -> person.navn },
                    clock = fixedClock,
                )
            },
        )
        verify(dokumentSkatt).lagre(argThat { it shouldBe dokument.value }, argThat { it shouldBe tx })
        verifyNoMoreInteractions(personMock, pdfGeneratorMock, dokumentSkatt)
    }

    @Test
    fun `lager skattegrunnlag dersom søker har skattegrunnlag men ikke eps`() {
        val person = person()
        val bosituasjon = bosituasjonEpsUnder67()
        val eps = person(fnr = bosituasjon.fnr)
        val vedtak = iverksattSøknadsbehandling(
            customVilkår = listOf(formuevilkårMedEps0Innvilget(bosituasjon = nonEmptyListOf(bosituasjon))),
            customGrunnlag = listOf(bosituasjon),
            eksterneGrunnlag = eksternGrunnlagHentet().copy(
                skatt = EksterneGrunnlagSkatt.Hentet(
                    nySkattegrunnlag(),
                    nySkattegrunnlagMedFeilIÅrsgrunnlag(fnr = eps.ident.fnr),
                ),
            ),
        ).third
        val personMock = mock<PersonOppslag> { on { this.person(any()) } doReturn person.right() }
        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<PdfInnhold>()) } doReturn pdfATom().right()
        }
        val dokumentSkatt = mock<DokumentSkattRepo> {}

        val service = mockedServices(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            dokumentSkattRepo = dokumentSkatt,
            journalførSkattDokumentService = mock(),
            clock = fixedClock,
        ).service
        val tx = TestSessionFactory.transactionContext
        val dokument = service.genererOgLagre(vedtak, tx)
        dokument.shouldBeRight()

        val captor = argumentCaptor<Fnr>()
        verify(personMock, times(2)).person(captor.capture())
        captor.allValues.size shouldBe 2
        captor.firstValue shouldBe person.ident.fnr
        captor.lastValue shouldBe eps.ident.fnr
        verify(pdfGeneratorMock).genererPdf(
            argThat<PdfInnhold> {
                it shouldBe SkattegrunnlagsPdfInnhold.lagSkattegrunnlagsPdf(
                    saksnummer = vedtak.saksnummer,
                    søknadsbehandlingsId = vedtak.behandling.id,
                    vedtaksId = vedtak.id,
                    hentet = fixedTidspunkt,
                    skatt = ÅrsgrunnlagForPdf(
                        søkers = ÅrsgrunnlagMedFnr(
                            fnr = person.ident.fnr,
                            årsgrunnlag = nonEmptyListOf(nySamletSkattegrunnlagForÅrOgStadieOppgjør()),
                        ),
                        eps = ÅrsgrunnlagMedFnr(
                            fnr = eps.ident.fnr,
                            årsgrunnlag = nonEmptyListOf(nySamletSkattegrunnlagForÅrOgStadieOppgjørMedFeilIÅrsgrunnlag()),
                        ),
                    ),
                    hentNavn = { _ -> person.navn },
                    clock = fixedClock,
                )
            },
        )
        verify(dokumentSkatt).lagre(argThat { it shouldBe dokument.value }, argThat { it shouldBe tx })
        verifyNoMoreInteractions(personMock, pdfGeneratorMock, dokumentSkatt)
    }

    @Test
    fun `lager skattegrunnlag dersom søker ikke har skattemelding, men eps har`() {
        val person = person()
        val bosituasjon = bosituasjonEpsUnder67()
        val eps = person(fnr = bosituasjon.fnr)
        val vedtak = iverksattSøknadsbehandling(
            customVilkår = listOf(formuevilkårMedEps0Innvilget(bosituasjon = nonEmptyListOf(bosituasjon))),
            customGrunnlag = listOf(bosituasjon),
            eksterneGrunnlag = eksternGrunnlagHentet().copy(
                skatt = EksterneGrunnlagSkatt.Hentet(
                    søkers = nySkattegrunnlagMedFeilIÅrsgrunnlag(),
                    eps = nySkattegrunnlag(fnr = bosituasjon.fnr),
                ),
            ),
        ).third
        val personMock = mock<PersonOppslag> { on { this.person(any()) } doReturn person.right() }
        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<PdfInnhold>()) } doReturn pdfATom().right()
        }
        val dokumentSkatt = mock<DokumentSkattRepo> {}

        val service = mockedServices(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            dokumentSkattRepo = dokumentSkatt,
            journalførSkattDokumentService = mock(),
            clock = fixedClock,
        ).service
        val tx = TestSessionFactory.transactionContext
        val dokument = service.genererOgLagre(vedtak, tx)
        dokument.shouldBeRight()

        val captor = argumentCaptor<Fnr>()
        verify(personMock, times(2)).person(captor.capture())
        captor.allValues.size shouldBe 2
        captor.firstValue shouldBe person.ident.fnr
        captor.lastValue shouldBe eps.ident.fnr
        verify(pdfGeneratorMock).genererPdf(
            argThat<PdfInnhold> {
                it shouldBe SkattegrunnlagsPdfInnhold.lagSkattegrunnlagsPdf(
                    saksnummer = vedtak.saksnummer,
                    søknadsbehandlingsId = vedtak.behandling.id,
                    vedtaksId = vedtak.id,
                    hentet = fixedTidspunkt,
                    skatt = ÅrsgrunnlagForPdf(
                        søkers = ÅrsgrunnlagMedFnr(
                            fnr = person.ident.fnr,
                            årsgrunnlag = nonEmptyListOf(nySamletSkattegrunnlagForÅrOgStadieOppgjørMedFeilIÅrsgrunnlag()),
                        ),
                        eps = ÅrsgrunnlagMedFnr(
                            fnr = bosituasjon.fnr,
                            årsgrunnlag = nonEmptyListOf(nySamletSkattegrunnlagForÅrOgStadieOppgjør()),
                        ),
                    ),
                    hentNavn = { _ -> person.navn },
                    clock = fixedClock,
                )
            },
        )
        verify(dokumentSkatt).lagre(argThat { it shouldBe dokument.value }, argThat { it shouldBe tx })
        verifyNoMoreInteractions(personMock, pdfGeneratorMock, dokumentSkatt)
    }

    @Test
    fun `lager skattedokument selv om søker og eps ikke har skattemelding`() {
        val person = person()
        val bosituasjon = bosituasjonEpsUnder67()
        val eps = person(fnr = bosituasjon.fnr)
        val vedtak = iverksattSøknadsbehandling(
            customVilkår = listOf(formuevilkårMedEps0Innvilget(bosituasjon = nonEmptyListOf(bosituasjon))),
            customGrunnlag = listOf(bosituasjon),
            eksterneGrunnlag = nyEksternGrunnlagHentetFeil(eps.ident.fnr),
        ).third

        val personMock = mock<PersonOppslag> { on { this.person(any()) } doReturn person.right() }
        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<PdfInnhold>()) } doReturn pdfATom().right()
        }
        val dokumentSkatt = mock<DokumentSkattRepo> {}

        val service = mockedServices(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            dokumentSkattRepo = dokumentSkatt,
            journalførSkattDokumentService = mock(),
            clock = fixedClock,
        ).service
        val tx = TestSessionFactory.transactionContext
        val dokument = service.genererOgLagre(vedtak, tx)
        dokument.shouldBeRight()

        val captor = argumentCaptor<Fnr>()
        verify(personMock, times(2)).person(captor.capture())
        captor.allValues.size shouldBe 2
        captor.firstValue shouldBe person.ident.fnr
        captor.lastValue shouldBe eps.ident.fnr
        verify(pdfGeneratorMock).genererPdf(
            argThat<PdfInnhold> {
                it shouldBe SkattegrunnlagsPdfInnhold.lagSkattegrunnlagsPdf(
                    saksnummer = vedtak.saksnummer,
                    søknadsbehandlingsId = vedtak.behandling.id,
                    vedtaksId = vedtak.id,
                    hentet = fixedTidspunkt,
                    skatt = ÅrsgrunnlagForPdf(
                        søkers = ÅrsgrunnlagMedFnr(
                            fnr = person.ident.fnr,
                            årsgrunnlag = nonEmptyListOf(nySamletSkattegrunnlagForÅrOgStadieOppgjørMedFeilIÅrsgrunnlag()),
                        ),
                        eps = ÅrsgrunnlagMedFnr(
                            fnr = eps.ident.fnr,
                            årsgrunnlag = nonEmptyListOf(nySamletSkattegrunnlagForÅrOgStadieOppgjørMedFeilIÅrsgrunnlag()),
                        ),
                    ),
                    hentNavn = { _ -> person.navn },
                    clock = fixedClock,
                )
            },
        )
        verify(dokumentSkatt).lagre(argThat { it shouldBe dokument.value }, argThat { it shouldBe tx })
        verifyNoMoreInteractions(personMock, pdfGeneratorMock, dokumentSkatt)
    }

    @Test
    fun `lager ikke skattegrunnlag dersom alle oppslagene er left `() {
        val vedtak = iverksattSøknadsbehandling(
            eksterneGrunnlag = eksternGrunnlagHentet().copy(
                skatt = EksterneGrunnlagSkatt.Hentet(
                    nySkattegrunnlagMedFeilIÅrsgrunnlag(),
                    nySkattegrunnlagMedFeilIÅrsgrunnlag(),
                ),
            ),
        ).third
        val person = person()
        val personMock = mock<PersonOppslag> { on { this.person(any()) } doReturn person.right() }
        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<PdfInnhold>()) } doReturn pdfATom().right()
        }

        val service = mockedServices(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            clock = fixedClock,
        ).service
        val tx = TestSessionFactory.transactionContext
        val dokument = service.genererOgLagre(vedtak, tx)
        dokument.shouldBeRight()
    }

    @Test
    fun `kan lage skatte pdf av skattegrunnlag`() {
        val skattegrunnlag = nySkattegrunnlag()
        val person = person()
        val pdfGenerator = mock<PdfGenerator> {
            on { genererPdf(any<PdfInnhold>()) } doReturn PdfA("content".toByteArray()).right()
        }
        val personOppslag = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }

        mockedServices(pdfGenerator = pdfGenerator, personOppslag = personOppslag).let {
            it.service.genererSkattePdf(
                request = GenererSkattPdfRequest(
                    skattegrunnlagSøkers = skattegrunnlag,
                    skattegrunnlagEps = null,
                    begrunnelse = "begrunnelse",
                    sakstype = Sakstype.UFØRE,
                    fagsystemId = "fagsystemId",
                ),
            ).shouldBeRight()

            verify(personOppslag).person(argThat { it shouldBe skattegrunnlag.fnr })
            verify(pdfGenerator).genererPdf(
                argThat<PdfInnhold> {
                    it.right() shouldBe lagSkattegrunnlagsPdfInnholdFraFrioppslag(
                        fagsystemId = "fagsystemId",
                        sakstype = Sakstype.UFØRE,
                        begrunnelse = "begrunnelse",
                        skattegrunnlagSøker = skattegrunnlag,
                        skattegrunnlagEps = null,
                        hentNavn = { _ -> person.navn.right() },
                        clock = fixedClock,
                    )
                },
            )
        }
        verifyNoMoreInteractions(pdfGenerator, personOppslag)
    }

    @Test
    fun `lager skatte pdf, med eps, og journalfører den`() {
        val pdf = pdfATom()
        val skattegrunnlag = nySkattegrunnlag()
        val skattegrunnlagEps = nySkattegrunnlag(fnr = epsFnr)
        val person = person()
        val pdfGenerator = mock<PdfGenerator> {
            on { genererPdf(any<PdfInnhold>()) } doReturn pdf.right()
        }
        val personOppslag = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        val journalførSkattDokumentService = mock<JournalførSkattDokumentService> {
            on { journalfør(any()) } doReturn JournalpostId("journalpostId").right()
        }

        mockedServices(
            pdfGenerator = pdfGenerator,
            personOppslag = personOppslag,
            journalførSkattDokumentService = journalførSkattDokumentService,
        ).let {
            it.service.genererSkattePdfOgJournalfør(
                GenererSkattPdfRequest(
                    skattegrunnlagSøkers = skattegrunnlag,
                    skattegrunnlagEps = skattegrunnlagEps,
                    begrunnelse = "begrunnelse",
                    sakstype = Sakstype.ALDER,
                    fagsystemId = "fagsystemId",
                ),
            ).shouldBeRight()

            val captor = argumentCaptor<Fnr>()
            verify(personOppslag, times(2)).person(captor.capture())
            captor.allValues.size shouldBe 2
            captor.firstValue shouldBe person.ident.fnr
            captor.lastValue shouldBe epsFnr
            verify(pdfGenerator).genererPdf(
                argThat<PdfInnhold> {
                    it.right() shouldBe lagSkattegrunnlagsPdfInnholdFraFrioppslag(
                        fagsystemId = "fagsystemId",
                        sakstype = Sakstype.ALDER,
                        begrunnelse = "begrunnelse",
                        skattegrunnlagSøker = skattegrunnlag,
                        skattegrunnlagEps = skattegrunnlagEps,
                        hentNavn = { _ -> person.navn.right() },
                        clock = fixedClock,
                    )
                },
            )

            verify(journalførSkattDokumentService).journalfør(
                argThat {
                    (it as JournalpostSkattUtenforSak) shouldBe JournalpostSkattUtenforSak.create(
                        fnr = fnr,
                        sakstype = Sakstype.ALDER,
                        fagsystemId = "fagsystemId",
                        dokument = Dokument.UtenMetadata.Informasjon.Annet(
                            id = it.dokument.id,
                            opprettet = fixedTidspunkt,
                            tittel = "Skattegrunnlag",
                            generertDokument = pdf,
                            generertDokumentJson = """
                                {
                                  "saksnummer":"fagsystemId",
                                  "behandlingstype":"Frioppslag",
                                  "sakstype": "ALDER",
                                  "behandlingsId":null,
                                  "vedtaksId":null,
                                  "hentet":"2021-01-01T01:02:03.456789Z",
                                  "opprettet":"2021-01-01T01:02:03.456789Z",
                                  "søkers":{
                                    "fnr":"$fnr",
                                    "navn":{
                                      "fornavn":"Tore",
                                      "mellomnavn":"Johnas",
                                      "etternavn":"Strømøy"
                                    },
                                    "årsgrunnlag":[
                                      {
                                        "type":"HarSkattegrunnlag",
                                        "år":2021,
                                        "stadie":"Oppgjør",
                                        "oppgjørsdato":null,
                                        "formue":[{"tekniskNavn":"bruttoformue","beløp":"1238","spesifisering":[]},{"tekniskNavn":"formuesverdiForKjoeretoey","beløp":"20000","spesifisering":[]}],
                                        "inntekt":[{"tekniskNavn":"alminneligInntektFoerSaerfradrag","beløp":"1000","spesifisering":[]}],
                                        "inntektsfradrag":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                                        "formuesfradrag":[{"tekniskNavn":"samletAnnenGjeld","beløp":"6000","spesifisering":[]}],
                                        "verdsettingsrabattSomGirGjeldsreduksjon":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                                        "oppjusteringAvEierinntekter":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                                        "manglerKategori":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                                        "annet":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                                        "kjøretøy":[{"beløp":"15000","registreringsnummer":"AB12345","fabrikatnavn":"Troll","årForFørstegangsregistrering":"1957","formuesverdi":"15000","antattVerdiSomNytt":null,"antattMarkedsverdi":null},{"beløp":"5000","registreringsnummer":"BC67890","fabrikatnavn":"Think","årForFørstegangsregistrering":"2003","formuesverdi":"5000","antattVerdiSomNytt":null,"antattMarkedsverdi":null}]
                                      }
                                    ]
                                  },
                                  "eps":{
                                    "fnr":"$epsFnr",
                                    "navn":{
                                        "fornavn":"Tore",
                                        "mellomnavn":"Johnas",
                                        "etternavn":"Strømøy"
                                    },
                                    "årsgrunnlag":[
                                        {
                                            "type":"HarSkattegrunnlag",
                                            "år":2021,
                                            "stadie":"Oppgjør",
                                            "oppgjørsdato":null,
                                            "formue":[{"tekniskNavn":"bruttoformue","beløp":"1238","spesifisering":[]},{"tekniskNavn":"formuesverdiForKjoeretoey","beløp":"20000","spesifisering":[]}],
                                            "inntekt":[{"tekniskNavn":"alminneligInntektFoerSaerfradrag","beløp":"1000","spesifisering":[]}],
                                            "inntektsfradrag":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                                            "formuesfradrag":[{"tekniskNavn":"samletAnnenGjeld","beløp":"6000","spesifisering":[]}],
                                            "verdsettingsrabattSomGirGjeldsreduksjon":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                                            "oppjusteringAvEierinntekter":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                                            "manglerKategori":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                                            "annet":[{"tekniskNavn":"fradragForFagforeningskontingent","beløp":"4000","spesifisering":[]}],
                                            "kjøretøy":[{"beløp":"15000","registreringsnummer":"AB12345","fabrikatnavn":"Troll","årForFørstegangsregistrering":"1957","formuesverdi":"15000","antattVerdiSomNytt":null,"antattMarkedsverdi":null},{"beløp":"5000","registreringsnummer":"BC67890","fabrikatnavn":"Think","årForFørstegangsregistrering":"2003","formuesverdi":"5000","antattVerdiSomNytt":null,"antattMarkedsverdi":null}]}]},
                                  "begrunnelse":"begrunnelse",
                                  "erAldersbrev":true
                                }
                            """.trimIndent().trimWhitespace(),
                        ),
                    )
                },
            )

            it.verifyNoMoreInteractions()
        }
    }

    private data class mockedServices(
        val pdfGenerator: PdfGenerator = mock(),
        val personOppslag: PersonOppslag = mock(),
        val dokumentSkattRepo: DokumentSkattRepo = mock(),
        val journalførSkattDokumentService: JournalførSkattDokumentService = mock(),
        val clock: Clock = fixedClock,
    ) {
        val service = SkattDokumentServiceImpl(
            pdfGenerator = pdfGenerator,
            personOppslag = personOppslag,
            dokumentSkattRepo = dokumentSkattRepo,
            journalførSkattDokumentService = journalførSkattDokumentService,
            clock = fixedClock,
        )

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(pdfGenerator, personOppslag, dokumentSkattRepo)
        }
    }
}
