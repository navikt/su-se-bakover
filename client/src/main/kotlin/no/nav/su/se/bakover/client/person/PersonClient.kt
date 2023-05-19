package no.nav.su.se.bakover.client.person

import arrow.core.Either
import arrow.core.right
import com.github.benmanes.caffeine.cache.Cache
import no.nav.su.se.bakover.client.cache.newCache
import no.nav.su.se.bakover.client.kodeverk.Kodeverk
import no.nav.su.se.bakover.client.krr.KontaktOgReservasjonsregister
import no.nav.su.se.bakover.client.skjerming.Skjerming
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.person.PersonOppslag
import java.time.Duration
import java.time.Year

internal data class PersonClientConfig(
    val kodeverk: Kodeverk,
    val skjerming: Skjerming,
    val kontaktOgReservasjonsregister: KontaktOgReservasjonsregister,
    val pdlClientConfig: PdlClientConfig,
)

/**
 * [FnrCacheKey] sørger for at rettighetene til brukerne blir ivaretatt, mens systembrukeren har tilgang til alt.
 *
 * @param personCache Brukes av både brukere og systembrukeren.
 * @param aktørIdCache Brukes av både brukere og systembrukeren.
 */
internal class PersonClient(
    private val config: PersonClientConfig,
    private val pdlClient: PdlClient = PdlClient(config.pdlClientConfig),
    private val hentBrukerToken: () -> JwtToken.BrukerToken = {
        JwtToken.BrukerToken.fraMdc()
    },
    private val personCache: Cache<FnrCacheKey, Person> = newCache(
        cacheName = "person",
        expireAfterWrite = Duration.ofMinutes(30),
    ),
    private val aktørIdCache: Cache<FnrCacheKey, AktørId> = newCache(
        cacheName = "aktoerId",
        expireAfterWrite = Duration.ofMinutes(30),
    ),
) : PersonOppslag {

    private fun <Value, Error> Cache<FnrCacheKey, Value>.getOrAdd(
        key: FnrCacheKey,
        mappingFunction: () -> Either<Error, Value>,
    ): Either<Error, Value> {
        return this.getIfPresent(key)?.right() ?: mappingFunction().onRight {
            this.put(key, it)
            if (key.second is JwtToken.BrukerToken) {
                // Dersom dette ble trigget av et brukertoken, ønsker vi å cache det for SystemToken også; men ikke andre veien.
                this.put(FnrCacheKey(key.first, JwtToken.SystemToken), it)
            }
        }
    }

    override fun person(
        fnr: Fnr,
    ): Either<KunneIkkeHentePerson, Person> {
        val brukerToken = hentBrukerToken()
        return personCache.getOrAdd(Pair(fnr, brukerToken)) {
            pdlClient.person(fnr, brukerToken).map { toPerson(it) }
        }
    }

    override fun personMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, Person> {
        return personCache.getOrAdd(Pair(fnr, JwtToken.SystemToken)) {
            pdlClient.personForSystembruker(fnr).map { toPerson(it) }
        }
    }

    override fun aktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
        val brukerToken = hentBrukerToken()
        return aktørIdCache.getOrAdd(Pair(fnr, brukerToken)) {
            pdlClient.aktørId(fnr, brukerToken)
        }
    }

    override fun aktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
        return aktørIdCache.getOrAdd(Pair(fnr, JwtToken.SystemToken)) {
            pdlClient.aktørIdMedSystembruker(fnr)
        }
    }

    override fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit> {
        val brukerToken = hentBrukerToken()
        return personCache.getOrAdd(Pair(fnr, brukerToken)) {
            pdlClient.person(fnr, brukerToken).map { toPerson(it) }
        }.map { }
    }

    private fun toPerson(pdlData: PdlData) = Person(
        ident = Ident(pdlData.ident.fnr, pdlData.ident.aktørId),
        navn = pdlData.navn.let {
            Person.Navn(
                fornavn = it.fornavn,
                mellomnavn = it.mellomnavn,
                etternavn = it.etternavn,
            )
        },
        telefonnummer = pdlData.telefonnummer,
        adresse = pdlData.adresse?.map {
            Person.Adresse(
                adresselinje = it.adresselinje,
                poststed = it.postnummer?.let { postnummer ->
                    toPoststed(postnummer)
                },
                bruksenhet = it.bruksenhet,
                kommune = it.kommunenummer?.let { kommunenummer ->
                    toKommune(kommunenummer)
                },
                landkode = it.landkode,
                adressetype = it.adressetype,
                adresseformat = it.adresseformat,
            )
        },
        statsborgerskap = pdlData.statsborgerskap,
        sivilstand = pdlData.sivilstand?.let {
            Person.Sivilstand(
                type = it.type,
                relatertVedSivilstand = it.relatertVedSivilstand?.let { fnrString -> Fnr(fnrString) },
            )
        },
        kjønn = pdlData.kjønn,
        fødsel = pdlData.fødsel?.let {
            if (it.foedselsdato != null) {
                Person.Fødsel.MedFødselsdato(it.foedselsdato)
            } else {
                Person.Fødsel.MedFødselsår(Year.of(it.foedselsaar))
            }
        },
        adressebeskyttelse = pdlData.adressebeskyttelse,
        skjermet = config.skjerming.erSkjermet(pdlData.ident.fnr),
        kontaktinfo = kontaktinfo(pdlData.ident.fnr),
        vergemål = pdlData.vergemålEllerFremtidsfullmakt,
        fullmakt = pdlData.fullmakt,
        dødsdato = pdlData.dødsdato,
    )

    private fun toPoststed(postnummer: String) = Person.Poststed(
        postnummer = postnummer,
        poststed = config.kodeverk.hentPoststed(postnummer).getOrNull(),
    )

    private fun toKommune(kommunenummer: String) = Person.Kommune(
        kommunenummer = kommunenummer,
        kommunenavn = config.kodeverk.hentKommunenavn(kommunenummer).getOrNull(),
    )

    private fun kontaktinfo(fnr: Fnr): Person.Kontaktinfo? {
        return config.kontaktOgReservasjonsregister.hentKontaktinformasjon(fnr).fold(
            {
                when (it) {
                    KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.BrukerErIkkeRegistrert -> null
                    KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.FeilVedHenting -> null
                }
            },
            {
                Person.Kontaktinfo(
                    epostadresse = it.epostadresse,
                    mobiltelefonnummer = it.mobiltelefonnummer,
                    språk = it.språk,
                    kanKontaktesDigitalt = it.kanKontaktesDigitalt(),
                )
            },
        )
    }
}

internal typealias FnrCacheKey = Pair<Fnr, JwtToken>
