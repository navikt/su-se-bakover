package no.nav.su.se.bakover.client.person

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.person.Variables.Companion.AKTORID
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.slf4j.MDC

const val NAV_CONSUMER_TOKEN = "Nav-Consumer-Token"
const val NAV_TEMA = "Tema"
const val SUP = "SUP"

internal class PdlClient(
    private val pdlUrl: String,
    private val tokenOppslag: TokenOppslag,
    private val azureClientId: String,
    private val oAuth: OAuth
) : PersonOppslag {
    companion object {
        private val logger = LoggerFactory.getLogger(PdlClient::class.java)
    }

    val hentPersonQuery = this::class.java.getResource("/hentPerson.graphql").readText()
    val hentIdenterQuery = this::class.java.getResource("/hentIdenter.graphql").readText()

    override fun person(fnr: Fnr): Either<ClientError, Person> {
        return kallpdl<PersonResponse>(fnr, hentPersonQuery).map { response ->
            response.data.hentPerson.navn.first {
                it.metadata.master == "Freg"
            }.let {
                Person(
                    fnr = fnr,
                    aktørId = hentIdent(response.data.hentIdenter),
                    fornavn = it.fornavn,
                    mellomnavn = it.mellomnavn,
                    etternavn = it.etternavn
                )
            }
        }
    }

    override fun aktørId(fnr: Fnr): Either<ClientError, AktørId> {
        return kallpdl<IdentResponse>(fnr, hentIdenterQuery).map {
            hentIdent(it.data.hentIdenter)
        }
    }

    private fun hentIdent(it: HentIdenter) =
        it.identer.filter { it.gruppe == AKTORID }.first().ident.let { AktørId(it) }

    private inline fun <reified T> kallpdl(fnr: Fnr, query: String): Either<ClientError, T> {
        val onBehalfOfToken = oAuth.onBehalfOFToken(MDC.get("Authorization"), azureClientId)
        val pdlRequest = PdlRequest(query, Variables(ident = fnr.toString()))
        val (_, response, result) = "$pdlUrl/graphql".httpPost()
            .header("Authorization", "Bearer $onBehalfOfToken")
            .header(NAV_CONSUMER_TOKEN, "Bearer ${tokenOppslag.token()}")
            .header(NAV_TEMA, SUP)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(objectMapper.writeValueAsString(pdlRequest))
            .responseString()
        return result.fold(
            { json ->
                JSONObject(json).let {
                    if (it.has("errors")) {
                        logger.warn("Feil i kallet mot pdl. status={}, body = {}", response.statusCode, json)
                        ClientError(response.statusCode, "Feil i kallet mot pdl").left()
                    } else {
                        objectMapper.readValue(json, T::class.java).right()
                    }
                }
            },
            {
                logger.warn("Feil i kallet mot pdl. status=${response.statusCode} body=${response.body().asString("application/json")}", it)
                ClientError(response.statusCode, "Feil i kallet mot pdl.").left()
            }
        )
    }
}

data class IdentResponse(
    val data: IdentResponseData
)
data class IdentResponseData(
    val hentIdenter: HentIdenter
)

data class PersonResponse(
    val data: PersonResponseData
)

data class PersonResponseData(
    val hentPerson: HentPerson,
    val hentIdenter: HentIdenter
)

data class HentPerson(
    val navn: List<Navn>
)

data class Navn(
    val etternavn: String,
    val fornavn: String,
    val mellomnavn: String?,
    val metadata: Metadata
)

data class Metadata(
    val master: String
)

data class HentIdenter(
    val identer: List<Ident>
)

data class Ident(
    val gruppe: String,
    val ident: String
)
