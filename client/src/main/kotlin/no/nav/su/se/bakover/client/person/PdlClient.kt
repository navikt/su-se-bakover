package no.nav.su.se.bakover.client.person

import Bostedsadresse
import Kontaktadresse
import Oppholdsadresse
import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import finnRiktigAdresseformatOgMapTilPdlAdresse
import no.nav.su.se.bakover.client.person.PdlData.Ident
import no.nav.su.se.bakover.client.person.PdlData.Navn
import no.nav.su.se.bakover.client.person.Variables.Companion.AKTORID
import no.nav.su.se.bakover.client.person.Variables.Companion.FOLKEREGISTERIDENT
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserializeParameterizedType
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.Tema
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson.FantIkkePerson
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson.IkkeTilgangTilPerson
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson.Ukjent
import no.nav.su.se.bakover.domain.person.Telefonnummer
import org.slf4j.LoggerFactory
import java.time.LocalDate

internal data class PdlClientConfig(
    val vars: ApplicationConfig.ClientsConfig.PdlConfig,
    val tokenOppslag: TokenOppslag,
    val azureAd: AzureAd,
)

// docs for vanlige folk: https://pdldocs-navno.msappproxy.net/ekstern/index.html#_f%C3%B8dsel
// api doc: https://github.com/navikt/pdl/blob/master/apps/api/src/main/resources/schemas/pdl.graphqls
// Du kan leke med de ulike queryene her (naisdevice): https://pdl-playground.dev.intern.nav.no/editor

internal class PdlClient(
    private val config: PdlClientConfig,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val hentPersonQuery = this::class.java.getResource("/hentPerson.graphql")?.readText()!!
    private val hentIdenterQuery = this::class.java.getResource("/hentIdenter.graphql")?.readText()!!

    fun person(fnr: Fnr, brukerToken: JwtToken.BrukerToken): Either<KunneIkkeHentePerson, PdlData> {
        return config.azureAd.onBehalfOfToken(brukerToken.value, config.vars.clientId).let { token ->
            kallPDLMedOnBehalfOfToken<PersonResponseData>(fnr, hentPersonQuery, token)
                .flatMap { mapResponse(it) }
        }
    }

    fun personForSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, PdlData> {
        return kallPDLMedSystembruker<PersonResponseData>(fnr, hentPersonQuery)
            .flatMap { mapResponse(it) }
    }

    private fun mapResponse(response: PersonResponseData): Either<KunneIkkeHentePerson, PdlData> {
        val person = response.hentPerson ?: return FantIkkePerson.left()
        val identer = response.hentIdenter ?: return FantIkkePerson.left()

        val pdlIdent = finnIdent(identer)

        if (person.navn.isEmpty()) {
            log.warn("Fant person i pdl, men feltene var tomme")
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
            sivilstand = person.sivilstand.firstOrNull(),
            kjønn = person.kjoenn.map { it.kjoenn }.firstOrNull(),
            fødsel = person.foedsel.map {
                PdlData.Fødsel(
                    foedselsaar = it.foedselsaar,
                    foedselsdato = it.foedselsdato,
                )
            }.firstOrNull(),
            adressebeskyttelse = person.adressebeskyttelse.firstOrNull()?.gradering,
            vergemålEllerFremtidsfullmakt = person.vergemaalEllerFremtidsfullmakt.isNotEmpty(),
            fullmakt = person.fullmakt.isNotEmpty(),
            dødsdato = person.doedsfall.let { doedsfall ->
                if (doedsfall.isEmpty()) {
                    null
                } else {
                    doedsfall.firstNotNullOfOrNull { it.doedsdato }.let {
                        if (it == null) {
                            log.error("Hentet en person som er registrert død, uten dødsdato. Se sikker logg for innhold")
                            sikkerLogg.error("Hentet en person som er registrert død, uten dødsdato. Person=$person")
                        }
                        it
                    }
                }
            },
        ).right()
    }

    private fun folkeregisteretAsMaster(metadata: Metadata) = metadata.master.lowercase() == "freg"

    fun aktørId(fnr: Fnr, brukerToken: JwtToken.BrukerToken): Either<KunneIkkeHentePerson, AktørId> {
        return config.azureAd.onBehalfOfToken(brukerToken.value, config.vars.clientId).let { token ->
            kallPDLMedOnBehalfOfToken<IdentResponseData>(fnr, hentIdenterQuery, token).map {
                val identer = it.hentIdenter ?: return FantIkkePerson.left()
                finnIdent(identer).aktørId
            }
        }
    }

    fun aktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
        return kallPDLMedSystembruker<IdentResponseData>(fnr, hentIdenterQuery).map {
            val identer = it.hentIdenter ?: return FantIkkePerson.left()
            finnIdent(identer).aktørId
        }
    }

    private fun finnIdent(hentIdenter: HentIdenter) =
        PdlIdent(
            fnr = hentIdenter.identer.first { it.gruppe == FOLKEREGISTERIDENT }.ident.let { Fnr(it) },
            aktørId = hentIdenter.identer.first { it.gruppe == AKTORID }.ident.let { AktørId(it) },
        )

    private inline fun <reified T> kallPDLMedSystembruker(fnr: Fnr, query: String): Either<KunneIkkeHentePerson, T> {
        val pdlRequest = PdlRequest(query, Variables(ident = fnr.toString()))
        val token = "Bearer ${config.tokenOppslag.token().value}"
        val (_, response, result) = "${config.vars.url}/graphql".httpPost()
            .header("Authorization", token)
            // TODO jah: PDL faser ut denne i preprod ila. februar 23.
            .header("Nav-Consumer-Token", token)
            .header("Tema", Tema.SUPPLERENDE_STØNAD.value)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(serialize(pdlRequest))
            .responseString()
        return håndterPdlSvar(result, response)
    }

    private inline fun <reified T> kallPDLMedOnBehalfOfToken(
        fnr: Fnr,
        query: String,
        jwtOnBehalfOf: String,
    ): Either<KunneIkkeHentePerson, T> {
        val pdlRequest = PdlRequest(query, Variables(ident = fnr.toString()))
        val (_, response, result) = "${config.vars.url}/graphql".httpPost()
            .header("Authorization", "Bearer $jwtOnBehalfOf")
            .header("Tema", Tema.SUPPLERENDE_STØNAD.value)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(serialize(pdlRequest))
            .responseString()
        return håndterPdlSvar(result, response)
    }

    private inline fun <reified Inner> håndterPdlSvar(
        result: Result<String, FuelError>,
        response: Response,
    ): Either<KunneIkkeHentePerson, Inner> {
        return result.fold(
            {
                val pdlResponse: PdlResponse<Inner> = deserializeParameterizedType<PdlResponse<Inner>, Inner>(it)
                if (pdlResponse.hasErrors()) {
                    håndterPdlFeil(pdlResponse).left()
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

    private fun <T> håndterPdlFeil(pdlResponse: PdlResponse<T>): KunneIkkeHentePerson {
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
}

internal data class PdlResponse<T>(
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

internal data class PdlError(
    val message: String,
    val path: List<String>,
    val extensions: PdlExtension,
)

internal data class PdlExtension(
    val code: String,
)

internal data class IdentResponseData(
    val hentIdenter: HentIdenter?,
)

internal data class PersonResponseData(
    val hentPerson: HentPerson?,
    val hentIdenter: HentIdenter?,
)

internal data class HentPerson(
    val navn: List<NavnResponse>,
    val telefonnummer: List<TelefonnummerResponse>,
    val bostedsadresse: List<Bostedsadresse>,
    val kontaktadresse: List<Kontaktadresse>,
    val oppholdsadresse: List<Oppholdsadresse>,
    val statsborgerskap: List<Statsborgerskap>,
    val sivilstand: List<SivilstandResponse>,
    val kjoenn: List<Kjønn>,
    val foedsel: List<Fødsel>,
    val adressebeskyttelse: List<Adressebeskyttelse>,
    val vergemaalEllerFremtidsfullmakt: List<VergemaalEllerFremtidsfullmakt>,
    val fullmakt: List<Fullmakt>,
    val doedsfall: List<Doedsfall>,
)

internal data class NavnResponse(
    val etternavn: String,
    val fornavn: String,
    val mellomnavn: String?,
    val metadata: Metadata,
)

internal data class TelefonnummerResponse(
    val landskode: String,
    val nummer: String,
    val prioritet: Int,
)

internal data class Statsborgerskap(
    val land: String,
    val gyldigFraOgMed: LocalDate?,
    val gyldigTilOgMed: LocalDate?,
)

internal data class Metadata(
    val master: String,
)

internal data class HentIdenter(
    val identer: List<Id>,
)

internal data class Id(
    val gruppe: String,
    val ident: String,
)

internal data class Kjønn(
    val kjoenn: String,
)

internal data class Fødsel(
    val foedselsdato: LocalDate?,
    val foedselsaar: Int,
)

internal data class Adressebeskyttelse(
    val gradering: String,
)

internal data class VergemaalEllerFremtidsfullmakt(
    val type: String?,
    val vergeEllerFullmektig: VergeEllerFullmektig,
) {

    data class VergeEllerFullmektig(
        val motpartsPersonident: String,
    )
}

internal data class Fullmakt(
    val motpartsRolle: FullmaktsRolle,
    val gyldigFraOgMed: LocalDate,
    val gyldigTilOgMed: LocalDate,
) {
    @Suppress("unused")
    enum class FullmaktsRolle {
        FULLMAKTSGIVER,
        FULLMEKTIG,
    }
}

internal data class Doedsfall(
    val doedsdato: LocalDate?,
)
