package no.nav.su.se.bakover.dokument.infrastructure.client

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.nySkattegrunnlagsPdfInnhold
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import person.domain.Person
import java.util.UUID
import kotlin.random.Random

internal class PdfClientTest {

    private val søknadPdfInnhold = SøknadPdfInnhold.create(
        saksnummer = Saksnummer(Random.nextLong(2021, Long.MAX_VALUE)),
        søknadsId = UUID.randomUUID(),
        navn = Person.Navn("Tore", null, "Strømøy"),
        søknadOpprettet = Tidspunkt.EPOCH,
        søknadInnhold = søknadinnholdUføre(),
        clock = fixedClock,
    )
    private val søknadPdfInnholdJson = serialize(søknadPdfInnhold)

    @Test
    fun `should generate pdf successfully`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder
                    .willReturn(
                        WireMock.ok("pdf-byte-array-here"),
                    ),
            )
            val client = PdfClient(baseUrl())
            client.genererPdf(søknadPdfInnhold)
                .map { it } shouldBe PdfA("pdf-byte-array-here".toByteArray()).right()
        }
    }

    @Test
    fun `generer pdf for pdfInnhold`() {
        startedWireMockServerWithCorrelationId {
            val request = nySkattegrunnlagsPdfInnhold()
            val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo("/api/v1/genpdf/supdfgen/skattegrunnlag"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .withRequestBody(WireMock.equalTo(serialize(request)))

            stubFor(
                wiremockBuilder
                    .willReturn(
                        WireMock.ok("pdf-byte-array-here"),
                    ),
            )
            val client = PdfClient(baseUrl())
            client.genererPdf(request)
                .map { it } shouldBe PdfA("pdf-byte-array-here".toByteArray()).right()
        }
    }

    @Test
    fun `returns KunneIkkeGenererePdf`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder
                    .willReturn(
                        WireMock.forbidden(),
                    ),
            )
            val client = PdfClient(baseUrl())

            client.genererPdf(søknadPdfInnhold) shouldBe KunneIkkeGenererePdf.left()
        }
    }

    @Test
    fun `fjerner control characters (literal)`() {
        val input = """
0. \u0000 0.1. \u0001 1.2. \u0002 2.3. \u0003 3.4. \u0004 4.5. \u0005 5.6. \u0006 6.7. \u0007 7.8. \u0008 8.9. \u0009 9.
10. \u000A 10.11. \u000B 11.12. \u000C 12.13. \u000D 13.14. \u000E 14.15. \u000F 15.16. \u0010 16.17. \u0011 17.18. \u0012 18.19. \u0013 19.
20. \u0014 20.21. \u0015 21.22. \u0016 22.23. \u0017 23.24. \u0018 24.25. \u0019 25.26. \u001A 26.27. \u001B 27.28. \u001C 28.29. \u001D 29.
30. \u001E 30.31. \u001F 31."""
        XmlValidString.create(input).value shouldBe "0.  0.1.  1.2.  2.3.  3.4.  4.5.  5.6.  6.7.  7.8.  8.9.  9.10.  10.11.  11.12.  12.13.  13.14.  14.15.  15.16.  16.17.  17.18.  18.19.  19.20.  20.21.  21.22.  22.23.  23.24.  24.25.  25.26.  26.27.  27.28.  28.29.  29.30.  30.31.  31."
    }

    @Test
    fun `fjerner control characters `() {
        val input = "a.\u0000b.\u0001c.\u0002d.\u0003e.\u0004f.\u0005g.\u0006h.\u0007i.\u0008j.\u0009k.\u000Al.\u000Bm.\u000Cn.\u000Do.\u000Dp.\u000Eq.\u000Er.\u000Es.\u000Et.\u000Eu.\u000Ev.\u000Ew.\u000Fx.\u000Fy.\u0010z.\u0011A.\u0012B.\u0013C.\u0014D.\u0015E.\u0016F.\u0017"
        XmlValidString.create(input).value shouldBe "a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y.z.A.B.C.D.E.F."
    }

    @Test
    fun `fjerner ikke unicodes etter 001F`() {
        val input = " \\u0020\\u0021\\u0022\u0020\u0021\u0022!\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~æøåÆØÅ\\n\\t "
        XmlValidString.create(input).value shouldBe input
    }

    private val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo("/api/v1/genpdf/supdfgen/soknad"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
        .withRequestBody(WireMock.equalTo(søknadPdfInnholdJson))
}
