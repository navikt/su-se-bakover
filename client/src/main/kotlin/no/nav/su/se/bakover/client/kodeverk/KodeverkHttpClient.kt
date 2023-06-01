package no.nav.su.se.bakover.client.kodeverk

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.client.kodeverk.Kodeverk.CouldNotGetKode
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal const val kodeverkPoststedPath = "/api/v1/kodeverk/Postnummer/koder/betydninger"
internal const val kodeverkKommunePath = "/api/v1/kodeverk/Kommuner/koder/betydninger"

class KodeverkHttpClient(val baseUrl: String, private val consumerId: String) : Kodeverk {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun hentPoststed(postnummer: String): Either<CouldNotGetKode, String?> {
        return hentKodebetydning(kodeverkPoststedPath, postnummer)
    }

    override fun hentKommunenavn(kommunenummer: String): Either<CouldNotGetKode, String?> {
        return hentKodebetydning(kodeverkKommunePath, kommunenummer)
    }

    private fun hentKodebetydning(path: String, value: String): Either<CouldNotGetKode, String?> {
        val (_, response, result) = "$baseUrl$path".httpGet()
            .header("Content-Type", "application/json")
            .header("Nav-Call-Id", getOrCreateCorrelationIdFromThreadLocal())
            .header("Nav-Consumer-Id", consumerId)
            .apply {
                parameters = listOf("ekskluderUgyldige" to "true", "spraak" to "nb")
            }
            .responseString()

        return result.fold(
            { json ->
                deserialize<KodeverkResponse>(json).betydninger.getOrDefault(value, emptyList())
                    .map { it.beskrivelser.nb.tekst }.firstOrNull().right()
            },
            {
                log.error(
                    "Feil i kallet mot kodeverk. status={} body={}",
                    response.statusCode,
                    response.body().asString("application/json"),
                    it,
                )
                CouldNotGetKode.left()
            },
        )
    }

    data class KodeverkResponse(
        val betydninger: Map<String, List<Betydning>>,
    )

    data class Betydning(
        val gyldigFra: String,
        val gyldigTil: String,
        val beskrivelser: Beskrivelser,
    )

    data class Beskrivelser(
        val nb: Beskrivelse,
    )

    data class Beskrivelse(
        val tekst: String,
        val term: String,
    )
}
