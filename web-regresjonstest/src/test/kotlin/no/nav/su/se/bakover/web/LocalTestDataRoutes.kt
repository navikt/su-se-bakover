package no.nav.su.se.bakover.web

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.extensions.endOfMonth
import no.nav.su.se.bakover.common.extensions.startOfMonth
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.routes.søknad.søknadPath
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknad
import no.nav.su.se.bakover.web.søknadsbehandling.opprettAvslåttSøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling

val localClient = HttpClient(Java) {
    defaultRequest {
        url("http://localhost:8080")
    }
}

internal fun Route.testDataRoutes() {
    data class NySøknadBody(
        val fnr: String?,
    )
    post("$søknadPath/dev/ny/uføre") {
        call.withBody<NySøknadBody> {
            val res = nyDigitalSøknad(
                fnr = it.fnr ?: Fnr.generer().toString(),
                client = localClient,
            )
            call.svar(Resultat.json(HttpStatusCode.OK, res))
        }
    }

    data class NySøknadsbehandlingBody(
        val fnr: String?,
        val resultat: String?,
        val fraOgMed: String?,
        val tilOgMed: String?,
    )
    post("søknadsbehandling/dev/ny/iverksatt") {
        call.withBody<NySøknadsbehandlingBody> {
            call.svar(
                Resultat.json(
                    HttpStatusCode.OK,
                    if (it.resultat == "innvilget") {
                        opprettInnvilgetSøknadsbehandling(
                            fnr = it.fnr ?: Fnr.generer().toString(),
                            fraOgMed = it.fraOgMed ?: fixedLocalDate.startOfMonth().toString(),
                            tilOgMed = it.tilOgMed ?: fixedLocalDate.startOfMonth().plusMonths(11).endOfMonth().toString(),
                            client = localClient,
                            appComponents = null,
                        )
                    } else {
                        opprettAvslåttSøknadsbehandling(
                            fnr = it.fnr ?: Fnr.generer().toString(),
                            fraOgMed = it.fraOgMed ?: fixedLocalDate.startOfMonth().toString(),
                            tilOgMed = it.tilOgMed ?: fixedLocalDate.startOfMonth().plusMonths(11).endOfMonth().toString(),
                            client = localClient,
                        )
                    },
                ),
            )
        }
    }
}
