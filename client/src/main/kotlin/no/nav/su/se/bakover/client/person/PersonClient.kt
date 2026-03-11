package no.nav.su.se.bakover.client.person

import arrow.core.Either
import no.nav.su.se.bakover.client.kodeverk.Kodeverk
import no.nav.su.se.bakover.client.krr.KontaktOgReservasjonsregister
import no.nav.su.se.bakover.client.skjerming.Skjerming
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import person.domain.AdresseopplysningerMedMetadata
import person.domain.Kontaktinfo
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import person.domain.PersonMedSkjermingOgKontaktinfo
import person.domain.PersonOppslag
import java.time.Year

internal data class PersonClientConfig(
    val kodeverk: Kodeverk,
    val skjerming: Skjerming,
    val kontaktOgReservasjonsregister: KontaktOgReservasjonsregister,
    val pdlClientConfig: PdlClientConfig,
)

/**
 * PDL-cache håndteres i [PdlClientWithCache] for å ivareta rettigheter mellom bruker- og systemtoken.
 */
internal class PersonClient(
    private val config: PersonClientConfig,
    suMetrics: SuMetrics,
    private val pdlClient: PdlClientWithCache = PdlClientWithCache(PdlClient(config.pdlClientConfig), suMetrics = suMetrics),
    private val hentBrukerToken: () -> JwtToken.BrukerToken = {
        JwtToken.BrukerToken.fraCoroutineContext()
    },
) : PersonOppslag {
    /**
     * PDL gjør en enkel tilgangssjekk implisitt ved kallet med brukertokenet
     */
    override fun person(
        fnr: Fnr,
        sakstype: Sakstype,
    ): Either<KunneIkkeHentePerson, Person> {
        val brukerToken = hentBrukerToken()
        return pdlClient.person(fnr, brukerToken, sakstype).map { toPerson(it, brukerToken) }
    }

    override fun personMedSystembruker(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, Person> {
        return pdlClient.personForSystembruker(fnr, sakstype).map { toPerson(it, JwtToken.SystemToken) }
    }

    override fun bostedsadresseMedMetadataForSystembruker(
        fnr: Fnr,
    ): Either<KunneIkkeHentePerson, AdresseopplysningerMedMetadata> {
        return pdlClient.bostedsadresseMedMetadataForSystembruker(fnr).map { data ->
            AdresseopplysningerMedMetadata(
                bostedsadresser = data.bostedsadresser.map { adresse ->
                    adresse.toDomainAdresseopplysning()
                },
            )
        }
    }

    override fun personMedSkjermingOgKontaktinfo(
        fnr: Fnr,
        sakstype: Sakstype,
    ): Either<KunneIkkeHentePerson, PersonMedSkjermingOgKontaktinfo> {
        val brukerToken = hentBrukerToken()
        return pdlClient.person(fnr, brukerToken, sakstype).map {
            PersonMedSkjermingOgKontaktinfo(
                person = toPerson(it, brukerToken),
                skjermet = config.skjerming.erSkjermet(it.ident.fnr, brukerToken),
                kontaktinfo = hentKontaktinfo(it.ident.fnr),
            )
        }
    }

    override fun aktørIdMedSystembruker(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, AktørId> {
        return pdlClient.aktørIdMedSystembruker(fnr, sakstype)
    }

    /**
     * En forenkling av [PersonOppslag.person] for å sjekke tilgang til personen uten at vi trenger å gjøre noe videre
     * med resultatet.
     * Denne sjekker adressebeskyttelse og skjerming(egen-ansatt) se https://pdl-docs.ansatt.nav.no/ekstern/index.html#_tilgangsstyring
     * MERK denne burde gå mot "pdl-pip-api"
     */
    override fun sjekkTilgangTilPerson(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, Unit> {
        val brukerToken = hentBrukerToken()
        return pdlClient.person(fnr, brukerToken, sakstype).map { }
    }

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

    private fun PdlBostedsadresseMedMetadata.Adresseopplysning.toDomainAdresseopplysning(): AdresseopplysningerMedMetadata.Adresseopplysning {
        return AdresseopplysningerMedMetadata.Adresseopplysning(
            historisk = historisk,
            hendelseIder = hendelseIder,
            gateadresse = gateadresse,
            postnummer = postnummer,
            folkeregistermetadata = folkeregistermetadata?.let {
                AdresseopplysningerMedMetadata.Folkeregistermetadata(
                    ajourholdstidspunkt = it.ajourholdstidspunkt,
                    gyldighetstidspunkt = it.gyldighetstidspunkt,
                    opphoerstidspunkt = it.opphoerstidspunkt,
                    kilde = it.kilde,
                    aarsak = it.aarsak,
                    sekvens = it.sekvens,
                )
            },
        )
    }

    private fun hentKontaktinfo(fnr: Fnr): Kontaktinfo? {
        return config.kontaktOgReservasjonsregister.hentKontaktinformasjon(fnr).fold(
            {
                when (it) {
                    KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.BrukerErIkkeRegistrert -> null
                    KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.FeilVedHenting -> null
                }
            },
            {
                Kontaktinfo(
                    epostadresse = it.epostadresse,
                    mobiltelefonnummer = it.mobiltelefonnummer,
                    språk = it.språk,
                    kanKontaktesDigitalt = it.kanKontaktesDigitalt(),
                )
            },
        )
    }
}
