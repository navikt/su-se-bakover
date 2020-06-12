package no.nav.su.se.bakover

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.request.receiveStream
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.kafka.SøknadMottattEmitter
import org.json.JSONObject

internal const val søknadPath = "/soknad"

@KtorExperimentalAPI
internal fun Route.soknadRoutes(
        mediator: SøknadRouteMediator
) {

    get("$søknadPath/{soknadId}") {
        Long.lesParameter(call, "soknadId").fold(
                left = { call.svar(BadRequest.tekst(it)) },
                right = { id ->
                    call.audit("Henter søknad med id: $id")
                    mediator.hentSøknad(id).fold(
                            left = { call.svar(NotFound.tekst(it)) },
                            right = { call.svar(OK.json(it.toJson())) }
                    )
                }
        )
    }

    post(søknadPath) {
        launchWithContext(call) {
            call.receiveTextUTF8().let { json ->
                SøknadInnhold.fromJson(JSONObject(json)).let { søknadInnhold ->
                    Fødselsnummer.fraString(søknadInnhold.personopplysninger.fnr).fold(
                            left = { call.svar(BadRequest.tekst(it)) },
                            right = {
                                call.audit("Lagrer søknad for person: $it")
                                call.svar(Created.json(mediator.nySøknad(søknadInnhold).toJson()))
                            }
                    )
                }
            }
        }
    }
}

suspend inline fun ApplicationCall.receiveTextUTF8(): String = String(receiveStream().readBytes())

internal class SøknadRouteMediator(
        private val sakFactory: SakFactory,
        private val søknadFactory: SøknadFactory,
        private val stønadsperiodeFactory: StønadsperiodeFactory,
        private val søknadMottattEmitter: SøknadMottattEmitter
) {
    fun nySøknad(søknadInnhold: SøknadInnhold): Sak {
        val sak = sakFactory.forFnr(Fødselsnummer(søknadInnhold.personopplysninger.fnr))
        val søknad = søknadFactory.nySøknad(søknadInnhold)
        stønadsperiodeFactory.nyStønadsperiode(sak, søknad)
        søknadMottattEmitter.søknadMottatt(SøknadMottattEvent(
                sakId = sak.id(),
                søknadId = søknad.id(),
                søknadInnhold = søknadInnhold
        ))
        return sakFactory.forFnr(Fødselsnummer(søknadInnhold.personopplysninger.fnr))
    }

    fun hentSøknad(id: Long) = søknadFactory.forId(id)


    data class SøknadMottattEvent(
            val sakId: Long,
            val søknadId: Long,
            val søknadInnhold: SøknadInnhold
    )
}