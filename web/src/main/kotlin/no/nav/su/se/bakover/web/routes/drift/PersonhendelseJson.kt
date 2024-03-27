package no.nav.su.se.bakover.web.routes.drift

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.web.services.personhendelser.KunneIkkeMappePersonhendelse
import no.nav.su.se.bakover.web.services.personhendelser.OpplysningstypeForPersonhendelse
import person.domain.SivilstandTyper
import java.time.LocalDate

internal data class PersonhendelseJson(
    // uuid
    @JsonProperty("_id")
    val id: String,
    val data: Data,
    // blanding av fnr+aktørid
    val identer: List<String>,
    // F.eks. 12345
    val offset: Long,
    // F.eks. 0
    val partition: Int,
    val timestamp: Timestamp,
) {
    data class Timestamp(
        // F.eks. 2021-09-02T12:00:00.000Z
        @JsonProperty("\$date")
        val date: String,
    )

    data class Data(
        // uuid
        val hendelseId: String,
        // blanding av fnr+aktørid, trolig alltid lik som identer
        val personidenter: List<String>,
        // F.eks. FREG
        val master: String,
        // millisek siden epoch
        val opprettet: String,
        // f.eks. SIVILSTAND_V1
        val opplysningstype: String,
        // f.eks. OPPRETTET
        val endringstype: String,
        // uuid
        val tidligereHendelseId: String?,
        val doedsfall: Doedsfall?,
        val sivilstand: Sivilstand?,
        val utflyttingFraNorge: UtflyttingFraNorge?,
        val bostedsadresse: Bostedsadresse?,
        val kontaktadresse: Kontaktadresse?,
    ) {
        data class Doedsfall(
            val doedsdato: String?,
        )

        data class Sivilstand(
            // f.eks. SKILT
            val type: String?,
            // dager siden epoch
            val gyldigFraOgMed: String?,
            // fnr
            val relatertVedSivilstand: String?,
            val bekreftelsesdato: String?,
        )

        data class UtflyttingFraNorge(
            // F.eks. XUK
            val tilflyttingsland: String?,
            val tilflyttingsstedIUtlandet: String?,
            // dager siden epoch
            val utflyttingsdato: String?,
        )

        data class Bostedsadresse(
            // dager siden epoch
            val angittFlyttedato: String?,
            // dager siden epoch
            val gyldigFraOgMed: String?,
            // dager siden epoch
            val gyldigTilOgMed: String?,
            val coAdressenavn: String?,
            val vegadresse: Vegadresse?,
            val matrikkeladresse: Matrikkeladresse?,
            val utenlandskAdresse: UtenlandskAdresse?,
            val ukjentBosted: UkjentBosted?,
        ) {
            data class UkjentBosted(
                // F.eks. 0301
                val bostedskommune: String?,
            )
        }

        data class Vegadresse(
            // F.eks 9 siffer
            val matrikkelId: String?,
            // F.eks. 1
            val husnummer: String?,
            // F.eks. B
            val husbokstav: String?,
            // F.eks. 0101
            val bruksenhetsnummer: String?,
            // F.eks. Storgata
            val adressenavn: String?,
            // F.eks. 0301
            val kommunenummer: String?,
            // F.eks. 030103
            val bydelsnummer: String?,
            // F.eks. 1
            val tilleggsnavn: String?,
            // F.eks. 0001
            val postnummer: String?,
            val koordinater: Koordinater?,
        )

        data class Matrikkeladresse(
            // F.eks 9 siffer
            val matrikkelId: String?,
            // F.eks. 0101
            val bruksenhetsnummer: String?,
            // F.eks. HVALSMOEN
            val tilleggsnavn: String?,
            // F.eks. 0001
            val postnummer: String?,
            // F.eks. 0301
            val kommunenummer: String?,
            val koordinater: Koordinater?,
        )

        data class UtenlandskAdresse(
            // F.eks. 1
            val adressenavnNummer: String?,
            // F.eks. 1
            val bygningEtasjeLeilighet: String?,
            // F.eks. 1
            val postboksNummerNavn: String?,
            // F.eks. 0001
            val postkode: String?,
            // F.eks. LONDON
            val bySted: String?,
            // F.eks. tom string
            val regionDistriktOmraade: String?,
            // F.eks. FR
            val landkode: String?,
        )

        data class Koordinater(
            // F.eks 6 siffer
            val x: Long?,
            // F.eks 7 siffer
            val y: Long?,
            // F.eks. 0
            val z: Long?,
        )

        data class Kontaktadresse(
            // dager siden epoch
            val gyldigFraOgMed: String?,
            // dager siden epoch
            val gyldigTilOgMed: String?,
            // F.eks. Utland
            val type: String?,
            val coAdressenavn: String?,
            val postboksadresse: Postboksadresse?,
            val vegadresse: Vegadresse?,
            val postadresseIFrittFormat: PostadresseIFrittFormat?,
            val utenlandskAdresse: UtenlandskAdresse?,
            val utenlandsAdresseIFrittFormat: String?,
        ) {
            data class PostadresseIFrittFormat(
                val adresselinje1: String?,
                val adresselinje2: String?,
                val adresselinje3: String?,
                val postnummer: String?,
            )
        }

        data class Postboksadresse(
            // F.eks. NAV
            val postbokseier: String?,
            // F.eks. 0001
            val postboks: String?,
            // F.eks. 0001
            val postnummer: String?,
        )
    }

    fun toDomain(): Either<KunneIkkeMappePersonhendelse.IkkeAktuellOpplysningstype, Personhendelse.IkkeTilknyttetSak> {
        val hendelseId = data.hendelseId
        val opplysningstype = data.opplysningstype
        val hendelse = when (opplysningstype) {
            OpplysningstypeForPersonhendelse.DØDSFALL.value -> Personhendelse.Hendelse.Dødsfall(
                dødsdato = data.doedsfall?.doedsdato?.fraPersonhendelseDatoTilLocalDate(),
            )

            OpplysningstypeForPersonhendelse.BOSTEDSADRESSE.value -> Personhendelse.Hendelse.Bostedsadresse
            OpplysningstypeForPersonhendelse.KONTAKTADRESSE.value -> Personhendelse.Hendelse.Kontaktadresse
            OpplysningstypeForPersonhendelse.SIVILSTAND.value -> Personhendelse.Hendelse.Sivilstand(
                type = SivilstandTyper.fromString(data.sivilstand?.type).getOrElse {
                    throw IllegalArgumentException("Personhendelse: Ukjent sivilstandstype: ${it.value} for hendelsesid $hendelseId, partisjon $partition og offset $offset")
                },
                gyldigFraOgMed = data.sivilstand?.gyldigFraOgMed?.fraPersonhendelseDatoTilLocalDate(),
                relatertVedSivilstand = data.sivilstand?.relatertVedSivilstand?.let {
                    Fnr.tryCreate(it)
                        ?: throw IllegalArgumentException("Kunne ikke parse fnr ved lesing av personhendelse fra Json")
                },
                bekreftelsesdato = data.sivilstand?.bekreftelsesdato?.fraPersonhendelseDatoTilLocalDate(),
            )

            OpplysningstypeForPersonhendelse.UTFLYTTING_FRA_NORGE.value -> Personhendelse.Hendelse.UtflyttingFraNorge(
                utflyttingsdato = data.utflyttingFraNorge?.utflyttingsdato?.fraPersonhendelseDatoTilLocalDate(),
            )

            else -> return KunneIkkeMappePersonhendelse.IkkeAktuellOpplysningstype(
                hendelseId = hendelseId,
                opplysningstype = opplysningstype,
            ).left()
        }
        return Personhendelse.IkkeTilknyttetSak(
            endringstype = when (data.endringstype) {
                "OPPRETTET" -> Personhendelse.Endringstype.OPPRETTET
                "KORRIGERT" -> Personhendelse.Endringstype.KORRIGERT
                "ANNULLERT" -> Personhendelse.Endringstype.ANNULLERT
                "OPPHOERT" -> Personhendelse.Endringstype.OPPHØRT
                else -> throw IllegalArgumentException("Personhendelse: Ukjent endringstype: ${data.endringstype} for hendelsesid $hendelseId, partisjon $partition og offset $offset")
            },
            hendelse = hendelse,
            metadata = Personhendelse.Metadata(
                hendelseId = hendelseId,
                personidenter = data.personidenter.toNonEmptyList(),
                tidligereHendelseId = data.tidligereHendelseId,
                offset = offset,
                partisjon = partition,
                master = data.master,
                key = "Innlest fra fil uten key. _id: $id",
                eksternOpprettet = this.timestamp.date.let { Tidspunkt.parse(it) },
            ),
        ).right()
    }
}

fun String.fraPersonhendelseDatoTilLocalDate(): LocalDate {
    return LocalDate.EPOCH.plusDays(this.toLong())
}
