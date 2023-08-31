package no.nav.su.se.bakover.service.skatt

import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagsPdfInnhold
import no.nav.su.se.bakover.client.pdf.ÅrsgrunnlagForPdf
import no.nav.su.se.bakover.client.pdf.ÅrsgrunnlagMedFnr
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.jsonRequest.PdfInnhold
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.bosituasjonEpsUnder67
import no.nav.su.se.bakover.test.eksterneGrunnlag.eksternGrunnlagHentet
import no.nav.su.se.bakover.test.eksterneGrunnlag.nyEksternGrunnlagHentetFeil
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
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

        val service = SkattDokumentServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            dokumentSkattRepo = dokumentSkatt,
            clock = fixedClock,
        )
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

        val service = SkattDokumentServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            dokumentSkattRepo = dokumentSkatt,
            clock = fixedClock,
        )
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

        val service = SkattDokumentServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            dokumentSkattRepo = dokumentSkatt,
            clock = fixedClock,
        )
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

        val service = SkattDokumentServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            dokumentSkattRepo = dokumentSkatt,
            clock = fixedClock,
        )
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

        val service = SkattDokumentServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            dokumentSkattRepo = dokumentSkatt,
            clock = fixedClock,
        )
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
        val dokumentSkatt = mock<DokumentSkattRepo> {}

        val service = SkattDokumentServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            dokumentSkattRepo = dokumentSkatt,
            clock = fixedClock,
        )
        val tx = TestSessionFactory.transactionContext
        val dokument = service.genererOgLagre(vedtak, tx)
        dokument.shouldBeRight()
    }
}
