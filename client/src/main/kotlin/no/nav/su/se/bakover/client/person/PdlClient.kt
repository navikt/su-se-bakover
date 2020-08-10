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
import no.nav.su.se.bakover.domain.Person.Adresse
import no.nav.su.se.bakover.domain.Person.Navn
import no.nav.su.se.bakover.domain.Telefonnummer
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.LocalDate

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
            val hentPerson = response.data.hentPerson
            val navn = hentPerson.navn.sortedBy {
                it.metadata.master == "Freg"
            }.first()
            val vegadresser = hentPerson.bostedsadresse.map { it.vegadresse } + hentPerson.oppholdsadresse.map { it.vegadresse } + hentPerson.kontaktadresse.map { it.vegadresse }
            // TODO jah: Don't throw exception if we can't find this person
            Person(
                fnr = fnr,
                aktørId = hentIdent(response.data.hentIdenter),
                navn = Navn(
                    fornavn = navn.fornavn,
                    mellomnavn = navn.mellomnavn,
                    etternavn = navn.etternavn
                ),
                telefonnummer = hentPerson.telefonnummer.firstOrNull()?.let {
                    Telefonnummer(landskode = it.landskode, nummer = it.nummer)
                },
                adresse = vegadresser.firstOrNull()?.let { adresse ->
                    Adresse(
                        adressenavn = adresse.adressenavn,
                        postnummer = adresse.postnummer,
                        poststed = null, // TODO: Oppslag postnummer -> poststed
                        bruksenhet = adresse.bruksenhetsnummer,
                        kommunenummer = adresse.kommunenummer,
                        kommunenavn = null // TODO: Oppslag kommunenummer -> kommunenavn
                    )
                },
                statsborgerskap = hentPerson.statsborgerskap.first().land
            )
        }
    }

    override fun aktørId(fnr: Fnr): Either<ClientError, AktørId> {
        return kallpdl<IdentResponse>(fnr, hentIdenterQuery).map {
            hentIdent(it.data.hentIdenter)
        }
    }

    private fun hentIdent(it: HentIdenter) =
        it.identer.first { it.gruppe == AKTORID }.ident.let { AktørId(it) } // TODO jah: Don't throw exception if we can't find this person

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
    val navn: List<NavnResponse>,
    val telefonnummer: List<TelefonnummerResponse>,
    val bostedsadresse: List<Bostedsadresse>,
    val kontaktadresse: List<Kontaktadresse>,
    val oppholdsadresse: List<Oppholdsadresse>,
    val statsborgerskap: List<Statsborgerskap>
)

data class NavnResponse(
    val etternavn: String,
    val fornavn: String,
    val mellomnavn: String?,
    val metadata: Metadata
)

data class TelefonnummerResponse(
    val landskode: String,
    val nummer: String,
    val prioritet: Int
)

data class Bostedsadresse(
    val vegadresse: Vegadresse?
)

data class Kontaktadresse(
    val vegadresse: Vegadresse?
)

data class Oppholdsadresse(
    val vegadresse: Vegadresse?
)

data class Statsborgerskap(
    val land: String,
    val gyldigFraOgMed: LocalDate?,
    val gyldigTilOgMed: LocalDate?
)

data class Vegadresse(
    val husnummer: String?,
    val husbokstav: String?,
    val adressenavn: String?,
    val kommunenummer: String?,
    val postnummer: String?,
    val bruksenhetsnummer: String?
)

data class Metadata(
    val opplysningsId: String?,
    val master: String,
    val historisk: Boolean
)

data class HentIdenter(
    val identer: List<Ident>
)

data class Ident(
    val gruppe: String,
    val ident: String
)
