package no.nav.su.se.bakover.client.historisk

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.deserializeMap
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.serialize
import org.slf4j.LoggerFactory

/**
 * Klient mot "Historisk Tidsbegrenset Uførestønad" (supstonad-historisk).
 * Swagger: https://supstonad-historisk.dev-fss-pub.nais.io/swagger-ui/index.html
 *
 * Tjenesten gir lesetilgang til historiske databasetabeller. [hentUttrekk] paginerer med [UttrekkResponse.iterator]:
 * send med iterator fra forrige respons i neste kall, og fortsett til [UttrekkResponse.iterator] er blank.
 */
interface SupstonadHistoriskClient {
    fun tellRader(tabellnavn: String): Either<ClientError, CountResponse>

    fun hentUttrekk(
        tabellnavn: String,
        antallRader: Long,
        iterator: String? = null,
    ): Either<ClientError, UttrekkResponse>

    fun hentTabeller(): Either<ClientError, Map<String, List<String>>>
}

class SupstonadHistoriskHttpClient(
    private val azureAd: AzureAd,
    private val url: String,
    private val clientId: String,
) : SupstonadHistoriskClient {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val baseUrl = if (url.endsWith("/")) url else "$url/"
    private val tellRaderUri = "api/tellRader"
    private val hentUttrekkUri = "api/hentUttrekk"
    private val hentTabellerUri = "tables"

    override fun tellRader(tabellnavn: String): Either<ClientError, CountResponse> {
        val (_, response, result) = "$baseUrl$tellRaderUri"
            .httpPost()
            .authentication().bearer(azureAd.getSystemToken(clientId))
            .header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            .header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            .body(serialize(CountRequest(tabellnavn)))
            .responseString()

        return result.fold(
            { json ->
                try {
                    deserialize<CountResponse>(json).right()
                } catch (e: Exception) {
                    log.error(
                        "Deserialization failed for tellRader fra supstonad-historisk. Responsstørrelse={} tegn",
                        json.length,
                        e,
                    )
                    ClientError(
                        HttpStatusCode.InternalServerError.value,
                        "Klarte ikke å deserialisere respons fra supstonad-historisk, se logg",
                    ).left()
                }
            },
            { error ->
                log.error("HTTP error fra supstonad-historisk ved tellRader", error)
                val body = response.body().toByteArray().toString(Charsets.UTF_8)
                ClientError(
                    httpStatus = error.response.statusCode,
                    message = body.ifBlank {
                        error.message ?: "Ukjent feil ved telling av rader i supstonad-historisk"
                    },
                ).left()
            },
        )
    }

    override fun hentUttrekk(
        tabellnavn: String,
        antallRader: Long,
        iterator: String?,
    ): Either<ClientError, UttrekkResponse> {
        val (_, response, result) = "$baseUrl$hentUttrekkUri"
            .httpPost()
            .authentication().bearer(azureAd.getSystemToken(clientId))
            .header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            .header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            .body(
                serialize(
                    UttrekkRequest(
                        tabellnavn = tabellnavn,
                        iterator = iterator,
                        antallRader = antallRader,
                    ),
                ),
            )
            .responseString()

        return result.fold(
            { json ->
                try {
                    deserialize<UttrekkResponse>(json).right()
                } catch (e: Exception) {
                    // Uttrekk kan inneholde store mengder personopplysninger. Responsen må aldri logges.
                    log.error(
                        "Deserialization failed for hentUttrekk fra supstonad-historisk. Responsstørrelse={} tegn",
                        json.length,
                        e,
                    )
                    ClientError(
                        HttpStatusCode.InternalServerError.value,
                        "Klarte ikke å deserialisere respons fra supstonad-historisk, se logg",
                    ).left()
                }
            },
            { error ->
                log.error("HTTP error fra supstonad-historisk ved hentUttrekk", error)
                val body = response.body().toByteArray().toString(Charsets.UTF_8)
                ClientError(
                    httpStatus = error.response.statusCode,
                    message = body.ifBlank {
                        error.message ?: "Ukjent feil ved henting av uttrekk fra supstonad-historisk"
                    },
                ).left()
            },
        )
    }

    override fun hentTabeller(): Either<ClientError, Map<String, List<String>>> {
        val (_, response, result) = "$baseUrl$hentTabellerUri"
            .httpGet()
            .authentication().bearer(azureAd.getSystemToken(clientId))
            .header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            .responseString()

        return result.fold(
            { json ->
                try {
                    deserializeMap<String, List<String>>(json).right()
                } catch (e: Exception) {
                    log.error(
                        "Deserialization failed for hentTabeller fra supstonad-historisk. Responsstørrelse={} tegn",
                        json.length,
                        e,
                    )
                    ClientError(
                        HttpStatusCode.InternalServerError.value,
                        "Klarte ikke å deserialisere respons fra supstonad-historisk, se logg",
                    ).left()
                }
            },
            { error ->
                log.error("HTTP error fra supstonad-historisk ved hentTabeller", error)
                val body = response.body().toByteArray().toString(Charsets.UTF_8)
                ClientError(
                    httpStatus = error.response.statusCode,
                    message = body.ifBlank {
                        error.message ?: "Ukjent feil ved henting av tabeller fra supstonad-historisk"
                    },
                ).left()
            },
        )
    }
}

data class CountRequest(
    val tabellnavn: String,
)

data class CountResponse(
    val antall: Long,
)

data class UttrekkRequest(
    val tabellnavn: String,
    val iterator: String? = null,
    val antallRader: Long,
)

data class UttrekkResponse(
    val iterator: String,
    val schema: SchemaDto,
    /** Database-NULL må bevares og ikke blandes sammen med tom tekst. */
    val innhold: List<List<String?>>,
)

data class SchemaDto(
    val kolonner: List<KolonnebeskrivelseDto>,
)

data class KolonnebeskrivelseDto(
    val navn: String,
)
