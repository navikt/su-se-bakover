package no.nav.su.se.bakover.client.person

import Bostedsadresse
import Kontaktadresse
import Oppholdsadresse
import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpPost
import finnRiktigAdresseformatOgMapTilPdlAdresse
import no.finn.unleash.Unleash
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.person.PdlData.Ident
import no.nav.su.se.bakover.client.person.PdlData.Navn
import no.nav.su.se.bakover.client.person.Variables.Companion.AKTORID
import no.nav.su.se.bakover.client.person.Variables.Companion.FOLKEREGISTERIDENT
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Telefonnummer
import no.nav.su.se.bakover.domain.Tema
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson.FantIkkePerson
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson.IkkeTilgangTilPerson
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson.Ukjent
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.LocalDate

internal data class PdlClientConfig(
    val vars: ApplicationConfig.ClientsConfig.PdlConfig,
    val tokenOppslag: TokenOppslag,
    val azureAd: OAuth,
    val unleash: Unleash,
)

internal class PdlClient(
    private val config: PdlClientConfig,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val hentPersonQuery = this::class.java.getResource("/hentPerson.graphql").readText()
    private val hentIdenterQuery = this::class.java.getResource("/hentIdenter.graphql").readText()

    private fun brukKunOnBehalfOfToken() = config.unleash.isEnabled("supstonad.ufore.pdl.bruk.obo.token", false)

    fun person(fnr: Fnr): Either<KunneIkkeHentePerson, PdlData> {
        return MDC.get("Authorization").let { jwt ->
            when (brukKunOnBehalfOfToken()) {
                false -> kallpdl<PersonResponseData>(fnr, hentPersonQuery, jwt)
                    .flatMap { mapResponse(it) }
                true -> kallPdlMedKunOnBehalfOfToken<PersonResponseData>(fnr, hentPersonQuery, config.azureAd.onBehalfOfToken(jwt, config.vars.clientId))
                    .flatMap { mapResponse(it) }
            }
        }
    }

    fun personForSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, PdlData> {
        return kallpdl<PersonResponseData>(fnr, hentPersonQuery, "Bearer ".plus(config.tokenOppslag.token()))
            .flatMap { mapResponse(it) }
    }

    private fun mapResponse(response: PersonResponseData): Either<KunneIkkeHentePerson, PdlData> {
        val person = response.hentPerson ?: return FantIkkePerson.left()
        val identer = response.hentIdenter ?: return FantIkkePerson.left()

        val pdlIdent = finnIdent(identer)

        if (person.navn.isNullOrEmpty()) {
            log.info("Fant person i pdl, men feltene var tomme")
            return FantIkkePerson.left()
        }

        val navn = person.navn.minByOrNull {
            folkeregisteretAsMaster(it.metadata)
        }!!

        val alleAdresser = listOf(
            person.bostedsadresse,
            person.oppholdsadresse,
            person.kontaktadresse,
        ).mapNotNull { it.firstOrNull() }.finnRiktigAdresseformatOgMapTilPdlAdresse()

        return PdlData(
            ident = Ident(pdlIdent.fnr, pdlIdent.aktørId),
            navn = Navn(
                fornavn = navn.fornavn,
                mellomnavn = navn.mellomnavn,
                etternavn = navn.etternavn,
            ),
            telefonnummer = person.telefonnummer.firstOrNull()?.let {
                Telefonnummer(landskode = it.landskode, nummer = it.nummer)
            },
            adresse = alleAdresser,
            statsborgerskap = person.statsborgerskap.firstOrNull()?.land,
            kjønn = person.kjoenn.map { it.kjoenn }.firstOrNull(),
            fødselsdato = person.foedsel.map { it.foedselsdato }.firstOrNull(),
            adressebeskyttelse = person.adressebeskyttelse.firstOrNull()?.gradering,
            vergemålEllerFremtidsfullmakt = person.vergemaalEllerFremtidsfullmakt.isNotEmpty(),
            fullmakt = person.fullmakt.isNotEmpty(),
        ).right()
    }

    private fun folkeregisteretAsMaster(metadata: Metadata) = metadata.master.lowercase() == "freg"

    fun aktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
        return when (brukKunOnBehalfOfToken()) {
            false -> kallpdl<IdentResponseData>(fnr, hentIdenterQuery, MDC.get("Authorization")).map {
                val identer = it.hentIdenter ?: return FantIkkePerson.left()
                finnIdent(identer).aktørId
            }
            true -> kallPdlMedKunOnBehalfOfToken<IdentResponseData>(fnr, hentIdenterQuery, config.azureAd.onBehalfOfToken(MDC.get("Authorization"), config.vars.clientId)).map {
                val identer = it.hentIdenter ?: return FantIkkePerson.left()
                finnIdent(identer).aktørId
            }
        }
    }

    private fun finnIdent(hentIdenter: HentIdenter) =
        PdlIdent(
            fnr = hentIdenter.identer.first { it.gruppe == FOLKEREGISTERIDENT }.ident.let { Fnr(it) },
            aktørId = hentIdenter.identer.first { it.gruppe == AKTORID }.ident.let { AktørId(it) },
        )

    private inline fun <reified T> kallpdl(fnr: Fnr, query: String, jwt: String): Either<KunneIkkeHentePerson, T> {
        val pdlRequest = PdlRequest(query, Variables(ident = fnr.toString()))
        val token = config.tokenOppslag.token()
        val (_, response, result) = "${config.vars.url}/graphql".httpPost()
            .header("Authorization", jwt)
            .header("Nav-Consumer-Token", "Bearer $token")
            .header("Tema", Tema.SUPPLERENDE_STØNAD.value)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(objectMapper.writeValueAsString(pdlRequest))
            .responseString()
        return result.fold(
            {
                val pdlResponse: PdlResponse<T> = objectMapper.readValue(it, specializedType(T::class.java))
                if (pdlResponse.hasErrors()) {
                    håndtererPdlFeil(pdlResponse).left()
                } else {
                    pdlResponse.data.right()
                }
            },
            {
                val statusCode = response.statusCode
                val body = response.body().asString("application/json")
                log.error("Feil i kallet mot PDL, status:$statusCode, body:$body", it)
                Ukjent.left()
            },
        )
    }

    private inline fun <reified T> kallPdlMedKunOnBehalfOfToken(fnr: Fnr, query: String, jwtOnBehalfOf: String): Either<KunneIkkeHentePerson, T> {
        val pdlRequest = PdlRequest(query, Variables(ident = fnr.toString()))
        val (_, response, result) = "${config.vars.url}/graphql".httpPost()
            .header("Authorization", jwtOnBehalfOf)
            .header("Tema", Tema.SUPPLERENDE_STØNAD.value)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(objectMapper.writeValueAsString(pdlRequest))
            .responseString()
        return result.fold(
            {
                val pdlResponse: PdlResponse<T> = objectMapper.readValue(it, specializedType(T::class.java))
                if (pdlResponse.hasErrors()) {
                    håndtererPdlFeil(pdlResponse).left()
                } else {
                    pdlResponse.data.right()
                }
            },
            {
                val statusCode = response.statusCode
                val body = response.body().asString("application/json")
                log.error("Feil i kallet mot PDL, status:$statusCode, body:$body", it)
                Ukjent.left()
            },
        )
    }

    private fun <T> håndtererPdlFeil(pdlResponse: PdlResponse<T>): KunneIkkeHentePerson {
        val feil = pdlResponse.toKunneIkkeHentePerson()
        if (feil.any { it is Ukjent }) {
            // Vi ønsker å logge ukjente respons-koder i alle tilfeller.
            log.error("Ukjent feilresponskode fra PDL: ${pdlResponse.errors}")
        }
        if (feil.groupBy { it.javaClass.kotlin }.size > 1) {
            // Greit å få oversikt om dette kan skje.
            log.error("Feilrespons fra PDL inneholdt forskjellige feilkoder: ${pdlResponse.errors}")
        }
        if (feil.any { it is IkkeTilgangTilPerson }) {
            return IkkeTilgangTilPerson
        }
        if (feil.any { it is Ukjent }) {
            return Ukjent
        }
        if (feil.all { it is FantIkkePerson }) {
            return FantIkkePerson
        }
        throw IllegalStateException("Implementation error - we didn't cover all the PDL error states.")
    }

    private fun specializedType(clazz: Class<*>) =
        objectMapper.typeFactory.constructParametricType(PdlResponse::class.java, clazz)
}

data class PdlResponse<T>(
    val data: T,
    val errors: List<PdlError>?,
) {
    fun hasErrors() = !errors.isNullOrEmpty()

    fun toKunneIkkeHentePerson(): List<KunneIkkeHentePerson> {
        return errors.orEmpty().map {
            resolveError(it.extensions.code)
        }
    }

    private fun resolveError(code: String) = when (code.lowercase()) {
        "not_found" -> FantIkkePerson
        "unauthorized" -> IkkeTilgangTilPerson
        else -> Ukjent
    }
}

data class PdlError(
    val message: String,
    val path: List<String>,
    val extensions: PdlExtension,
)

data class PdlExtension(
    val code: String,
)

data class IdentResponseData(
    val hentIdenter: HentIdenter?,
)

data class PersonResponseData(
    val hentPerson: HentPerson?,
    val hentIdenter: HentIdenter?,
)

data class HentPerson(
    val navn: List<NavnResponse>,
    val telefonnummer: List<TelefonnummerResponse>,
    val bostedsadresse: List<Bostedsadresse>,
    val kontaktadresse: List<Kontaktadresse>,
    val oppholdsadresse: List<Oppholdsadresse>,
    val statsborgerskap: List<Statsborgerskap>,
    val kjoenn: List<Kjønn>,
    val foedsel: List<Fødsel>,
    val adressebeskyttelse: List<Adressebeskyttelse>,
    val vergemaalEllerFremtidsfullmakt: List<VergemaalEllerFremtidsfullmakt>,
    val fullmakt: List<Fullmakt>,
)

data class NavnResponse(
    val etternavn: String,
    val fornavn: String,
    val mellomnavn: String?,
    val metadata: Metadata,
)

data class TelefonnummerResponse(
    val landskode: String,
    val nummer: String,
    val prioritet: Int,
)

data class Statsborgerskap(
    val land: String,
    val gyldigFraOgMed: LocalDate?,
    val gyldigTilOgMed: LocalDate?,
)

data class Metadata(
    val master: String,
)

data class HentIdenter(
    val identer: List<Id>,
)

data class Id(
    val gruppe: String,
    val ident: String,
)

data class Kjønn(
    val kjoenn: String,
)

data class Fødsel(
    val foedselsdato: LocalDate,
)

data class Adressebeskyttelse(
    val gradering: String,
)

data class VergemaalEllerFremtidsfullmakt(
    val type: String?,
    val vergeEllerFullmektig: VergeEllerFullmektig,
) {

    data class VergeEllerFullmektig(
        val motpartsPersonident: String,
    )
}

data class Fullmakt(
    val motpartsRolle: FullmaktsRolle,
    val gyldigFraOgMed: LocalDate,
    val gyldigTilOgMed: LocalDate,
) {
    enum class FullmaktsRolle {
        FULLMAKTSGIVER,
        FULLMEKTIG
    }
}
