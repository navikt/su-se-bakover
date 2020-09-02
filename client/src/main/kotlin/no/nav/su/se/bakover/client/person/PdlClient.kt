package no.nav.su.se.bakover.client.person

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.person.PdlData.Adresse
import no.nav.su.se.bakover.client.person.PdlData.Ident
import no.nav.su.se.bakover.client.person.PdlData.Navn
import no.nav.su.se.bakover.client.person.Variables.Companion.AKTORID
import no.nav.su.se.bakover.client.person.Variables.Companion.FOLKEREGISTERIDENT
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Telefonnummer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.LocalDate

const val NAV_CONSUMER_TOKEN = "Nav-Consumer-Token"
const val NAV_TEMA = "Tema"
const val SUP = "SUP"

internal class PdlClient(
    private val pdlUrl: String,
    private val tokenOppslag: TokenOppslag
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PdlClient::class.java)
    }

    val hentPersonQuery = this::class.java.getResource("/hentPerson.graphql").readText()
    val hentIdenterQuery = this::class.java.getResource("/hentIdenter.graphql").readText()

    fun person(fnr: Fnr): Either<PdlFeil, PdlData> {
        return kallpdl<PersonResponseData>(fnr, hentPersonQuery).map { response ->
            val hentPerson = response.hentPerson!!
            val navn = hentPerson.navn.sortedBy {
                folkeregisteretAsMaster(it.metadata)
            }.first()
            val vegadresser =
                hentPerson.bostedsadresse.map { it.vegadresse } + hentPerson.oppholdsadresse.map { it.vegadresse } + hentPerson.kontaktadresse.map { it.vegadresse }
            // TODO jah: Don't throw exception if we can't find this person
            PdlData(
                ident = Ident(hentIdent(response.hentIdenter!!).fnr, hentIdent(response.hentIdenter).aktørId),
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
                        husnummer = adresse.husnummer,
                        husbokstav = adresse.husbokstav,
                        postnummer = adresse.postnummer,
                        bruksenhet = adresse.bruksenhetsnummer,
                        kommunenummer = adresse.kommunenummer
                    )
                },
                statsborgerskap = hentPerson.statsborgerskap.firstOrNull()?.land,
                kjønn = hentPerson.kjoenn.map { it.kjoenn }.firstOrNull()
            )
        }
    }

    private fun folkeregisteretAsMaster(metadata: Metadata) = metadata.master.toLowerCase() == "freg"

    fun aktørId(fnr: Fnr): Either<PdlFeil, AktørId> {
        return kallpdl<IdentResponseData>(fnr, hentIdenterQuery).map {
            hentIdent(it.hentIdenter!!).aktørId
        }
    }

    private fun hentIdent(it: HentIdenter) =
        PdlIdent(
            fnr = it.identer.first { it.gruppe == FOLKEREGISTERIDENT }.ident.let { Fnr(it) },
            aktørId = it.identer.first { it.gruppe == AKTORID }.ident.let { AktørId(it) }
        )

    private inline fun <reified T> kallpdl(fnr: Fnr, query: String): Either<PdlFeil, T> {
        val pdlRequest = PdlRequest(query, Variables(ident = fnr.toString()))
        val token = tokenOppslag.token()
        logger.info("Authorization: " + MDC.get("Authorization"))
        logger.info("NAV_CONSUMER_TOKEN: $token")
        val (_, response, result) = "$pdlUrl/graphql".httpPost()
            .header("Authorization", MDC.get("Authorization"))
            .header(NAV_CONSUMER_TOKEN, "Bearer $token")
            .header(NAV_TEMA, SUP)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(objectMapper.writeValueAsString(pdlRequest))
            .responseString()
        return result.fold(
            {
                val pdlResponse: PdlResponse<T> = objectMapper.readValue(it, specializedType(T::class.java))
                if (pdlResponse.hasErrors()) {
                    logger.warn("Feil i kallet mod PDL: {}", pdlResponse)
                    PdlFeil.from(pdlResponse.errors!!).left()
                } else {
                    pdlResponse.data.right()
                }
            },
            {
                logger.warn(
                    "Feil i kallet mot PDL, status:{}, body:{}",
                    response.statusCode,
                    response.body().asString("application/json")
                )
                PdlFeil.Ukjent.left()
            }
        )
    }

    private fun specializedType(clazz: Class<*>) =
        objectMapper.typeFactory.constructParametricType(PdlResponse::class.java, clazz)
}

sealed class PdlFeil(
    val message: String
) {
    companion object {
        fun from(errors: List<PdlError>): PdlFeil {
            return if (errors.size == 1) {
                resolveError(errors.first().extensions.code)
            } else {
                Ukjent
            }
        }

        private fun resolveError(code: String) = when (code.toLowerCase()) {
            "not_found" -> FantIkkePerson
            else -> Ukjent
        }
    }

    object FantIkkePerson : PdlFeil("Fant ikke person i PDL")
    object Ukjent : PdlFeil("Ukjent feil mot PDL")
}

data class PdlResponse<T>(
    val data: T,
    val errors: List<PdlError>?
) {
    fun hasErrors() = !errors.isNullOrEmpty()
}

data class PdlError(
    val message: String,
    val path: List<String>,
    val extensions: PdlExtension
)

class PdlExtension(
    val code: String
)

data class IdentResponseData(
    val hentIdenter: HentIdenter?
)

data class PersonResponseData(
    val hentPerson: HentPerson?,
    val hentIdenter: HentIdenter?
)

data class HentPerson(
    val navn: List<NavnResponse>,
    val telefonnummer: List<TelefonnummerResponse>,
    val bostedsadresse: List<Bostedsadresse>,
    val kontaktadresse: List<Kontaktadresse>,
    val oppholdsadresse: List<Oppholdsadresse>,
    val statsborgerskap: List<Statsborgerskap>,
    val kjoenn: List<Kjønn>
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
    val identer: List<Id>
)

data class Id(
    val gruppe: String,
    val ident: String
)

data class Kjønn(
    val kjoenn: String
)
