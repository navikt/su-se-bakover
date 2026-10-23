package no.nav.su.se.bakover.web.inputvalidation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.ktor.server.routing.RoutingCall
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.receiveTextUTF8
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.jsonNode
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.Logger

/**
 * Hvorfor en request-body ble avvist. Feilen beskrives her, mens logging og svar til klienten
 * skjer én gang i [withValidertBody].
 */
internal sealed interface UgyldigBody {
    data class KunneIkkeLeses(val cause: Throwable) : UgyldigBody
    data class IkkeJson(val json: String, val cause: Throwable) : UgyldigBody
    data class UgyldigeFelt(val feil: List<UgyldigInput>) : UgyldigBody
    data class KunneIkkeDeserialisere(val json: String, val cause: Throwable) : UgyldigBody
}

/**
 * Som `withBody`, men validerer alle strengverdier i den rå JSON-bodyen med [InputValidator] før den
 * deserialiseres til [T]. Hvilke felt som er strenger, trenger derfor ikke listes opp per rute.
 *
 * Ved ugyldig tegn eller lengde besvares kallet med [tilUgyldigFeltSvar];
 * ved body som ikke kan leses, tolkes eller deserialiseres med [Feilresponser.ugyldigBody].
 * Feilen logges i begge tilfeller. [ifRight] kalles bare når alt er gyldig.
 *
 * @param maksLengde maks lengde per felt. Nøkkelen er stien uten listeindekser, bygget med [sti]
 * slik at et omdøpt felt gir kompileringsfeil. Felt som ikke er nevnt, får [InputFelter.FRITEKST].
 */
internal suspend inline fun <reified T> RoutingCall.withValidertBody(
    log: Logger,
    maksLengde: Map<String, Int> = emptyMap(),
    ifRight: (T) -> Unit,
) {
    either {
        val json = lesValidertJson(maksLengde).bind()
        Either.catch { deserialize<T>(json) }
            .mapLeft { UgyldigBody.KunneIkkeDeserialisere(json, it) }
            .bind()
    }.fold(
        ifLeft = { feil ->
            feil.logg(rutemal, log)
            svar(feil.tilResultat())
        },
        ifRight = ifRight,
    )
}

/**
 * Leser bodyen som tekst og validerer alle strengverdier i JSON-treet.
 * Returnerer den uendrede teksten, slik at den kan deserialiseres til en typet DTO.
 * Logger ikke og svarer ikke; det gjør kalleren ut fra [UgyldigBody].
 */
internal suspend fun RoutingCall.lesValidertJson(
    maksLengde: Map<String, Int>,
): Either<UgyldigBody, String> = either {
    val json = Either.catch { receiveTextUTF8() }.mapLeft { UgyldigBody.KunneIkkeLeses(it) }.bind()
    val tre = Either.catch { jsonNode(json) }.mapLeft { UgyldigBody.IkkeJson(json, it) }.bind()
    val feil = InputValidator.validerStrengverdier(tre, maksLengde)
    ensure(feil.isEmpty()) { UgyldigBody.UgyldigeFelt(feil) }
    json
}

internal fun UgyldigBody.tilResultat(): Resultat = when (this) {
    is UgyldigBody.KunneIkkeLeses,
    is UgyldigBody.IkkeJson,
    is UgyldigBody.KunneIkkeDeserialisere,
    -> Feilresponser.ugyldigBody

    is UgyldigBody.UgyldigeFelt -> feil.tilUgyldigFeltSvar()
}

internal fun UgyldigBody.logg(rutemal: String, log: Logger) {
    when (this) {
        is UgyldigBody.KunneIkkeLeses -> {
            log.error("Feil ved lesing av json-body inn mot web-laget, se sikkerlogg for detaljer.")
            sikkerLogg.error("Feil ved lesing av json-body.", cause)
        }

        is UgyldigBody.IkkeJson -> {
            log.error("Feil ved parsing av json-body inn mot web-laget, se sikkerlogg for detaljer.")
            sikkerLogg.error("Feil ved parsing av json-body: $json", cause)
        }

        is UgyldigBody.KunneIkkeDeserialisere -> {
            log.error("Feil ved deserialisering av json-body inn mot web-laget, se sikkerlogg for detaljer.")
            sikkerLogg.error("Feil ved deserialisering av json-body: $json", cause)
        }

        is UgyldigBody.UgyldigeFelt -> loggInputValidering(feil, rutemal, log, sikkerLogg)
    }
}
