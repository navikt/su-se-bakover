package no.nav.su.se.bakover.client.person

import arrow.core.Either
import no.nav.su.se.bakover.client.kodeverk.Kodeverk
import no.nav.su.se.bakover.client.krr.KontaktOgReservasjonsregister
import no.nav.su.se.bakover.client.skjerming.Skjerming
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import person.domain.PersonOppslag
import java.time.Year

internal data class PersonClientConfig(
    val kodeverk: Kodeverk,
    val skjerming: Skjerming,
    val kontaktOgReservasjonsregister: KontaktOgReservasjonsregister,
    val pdlClientConfig: PdlClientConfig,
)

internal class PersonClient(
    private val config: PersonClientConfig,
    private val suMetrics: SuMetrics,
    private val pdlClient: PdlClientWithCache = PdlClientWithCache(PdlClient(config.pdlClientConfig), suMetrics = suMetrics),
    private val hentBrukerToken: () -> JwtToken.BrukerToken = {
        JwtToken.BrukerToken.fraMdc()
    },
) : PersonOppslag {
    /**
     * PDL gjør en enkel tilgangssjekk implisitt ved kallet med brukertokenet
     */
    override fun person(
        fnr: Fnr,
    ): Either<KunneIkkeHentePerson, Person> {
        val brukerToken = hentBrukerToken()
        return pdlClient.person(fnr, brukerToken).map { toPerson(it, brukerToken) }
    }

    override fun personMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, Person> {
        return pdlClient.personForSystembruker(fnr).map { toPerson(it, JwtToken.SystemToken) }
    }

    override fun aktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
        return pdlClient.aktørIdMedSystembruker(fnr)
    }

    /**
     * En forenkling av [PersonOppslag.person] for å sjekke tilgang til personen uten at vi trenger å gjøre noe videre
     * med resultatet.
     * Kontaktinfo er ikke relevant for tilgangssjekk. Skjermet oppslaget burde ikke være med heller da man ikke tolker resultatet
     * Denne gjøres ofte i tillegg til selve datafetchingen men da blir det lagt i cache så burde ikke vært et stort problem.
     */
    override fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit> {
        val brukerToken = hentBrukerToken()
        return pdlClient.person(fnr, brukerToken).map { }
    }

    /**
     * Har to sideeffekter, ett kall til krr proxy og et kall til skjermingstjenesten.
     */
    private fun toPerson(pdlData: PdlData, token: JwtToken) = Person(
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
                    toPoststed(postnummer, token)
                },
                bruksenhet = it.bruksenhet,
                kommune = it.kommunenummer?.let { kommunenummer ->
                    toKommune(kommunenummer, token)
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
        fødsel = pdlData.fødsel?.let {
            if (it.foedselsdato != null) {
                Person.Fødsel.MedFødselsdato(it.foedselsdato)
            } else {
                Person.Fødsel.MedFødselsår(Year.of(it.foedselsaar))
            }
        },
        adressebeskyttelse = pdlData.adressebeskyttelse,
        skjermet = config.skjerming.erSkjermet(pdlData.ident.fnr),
        kontaktinfo = { kontaktinfo(pdlData.ident.fnr) }, // Kjører lazy loading på denne da den stort sett ikke brukes
        vergemål = pdlData.vergemålEllerFremtidsfullmakt,
        dødsdato = pdlData.dødsdato,
    )

    private fun toPoststed(postnummer: String, token: JwtToken) = Person.Poststed(
        postnummer = postnummer,
        poststed = config.kodeverk.hentPoststed(postnummer, token).getOrNull(),
    )

    private fun toKommune(kommunenummer: String, token: JwtToken) = Person.Kommune(
        kommunenummer = kommunenummer,
        kommunenavn = config.kodeverk.hentKommunenavn(kommunenummer, token).getOrNull(),
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
