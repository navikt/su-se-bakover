package no.nav.su.se.bakover.web.routes

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.routing.get
import io.ktor.routing.routing
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevRequest
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.brev.BrevService
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

private const val SØKNAD_TRUKKET = "/brevtemplate/søknadtrukket"
private const val NY_SØKNAD = "/brevtemplate/nysøknad"

internal fun Application.brevRoutes(brevService: BrevService, pdfGenerator: PdfGenerator) {
    routing {
        get(SØKNAD_TRUKKET) {

            val trukketSøknadBrevRequest = TrukketSøknadBrevRequest(
                person = Person(
                    ident = Ident(
                        fnr = FnrGenerator.random(), aktørId = AktørId("Aktørid"),
                    ),
                    navn = Person.Navn("Kåre", "", "Kropp"),
                ),
                søknad = Søknad.Ny(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    sakId = UUID.randomUUID(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                ),
                trukketDato = LocalDate.now(),
                saksbehandlerNavn = "saksbehandler",
            )
            brevService.lagBrev(trukketSøknadBrevRequest).fold(
                { call.respond("Error") },
                { call.respondBytes(bytes = it, contentType = ContentType.Application.Pdf) },
            )
        }

        get(NY_SØKNAD) {
            val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

            val søknadPdfInnhold = SøknadPdfInnhold.create(
                saksnummer = Saksnummer(Random.nextLong(2021, Long.MAX_VALUE)),
                søknadsId = UUID.randomUUID(),
                navn = Person.Navn("Tore", null, "Strømøy"),
                søknadOpprettet = Tidspunkt.EPOCH,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                clock = fixedClock,
            )
            pdfGenerator.genererPdf(søknadPdfInnhold).fold(
                { call.respond("Error") },
                { call.respondBytes(bytes = it, contentType = ContentType.Application.Pdf) },
            )
        }
    }
}
