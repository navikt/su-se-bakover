package no.nav.su.se.bakover.web.routes.drift

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.web.services.personhendelser.KunneIkkeMappePersonhendelse
import no.nav.su.se.bakover.web.services.personhendelser.OpplysningstypeForPersonhendelse
import person.domain.SivilstandTyper
import java.time.LocalDate

internal data class PersonhendelseJson(
    @JsonProperty("_id")
    val id: String, // uuid
    val data: Data,
    val identer: List<String>, // blanding av fnr+aktørid
    val offset: Long, // F.eks. 12345
    val partition: Int, // F.eks. 0
    val timestamp: Timestamp,
) {
    data class Timestamp(
        @JsonProperty("\$date")
        val date: String, // F.eks. 2021-09-02T12:00:00.000Z
    )

    data class Data(
        val hendelseId: String, //uuid
        val personidenter: List<String>, // blanding av fnr+aktørid, trolig alltid lik som identer
        val master: String, //F.eks. FREG
        val opprettet: String, // millisek siden epoch
        val opplysningstype: String, // f.eks. SIVILSTAND_V1
        val endringstype: String, // f.eks. OPPRETTET
        val tidligereHendelseId: String?, // uuid
        val doedsfall: Doedsfall?,
        val sivilstand: Sivilstand?,
        val utflyttingFraNorge: UtflyttingFraNorge?,
        val bostedsadresse: Bostedsadresse,
        val kontaktadresse: Kontaktadresse,
    ) {
        data class Doedsfall(
            val doedsdato: String?,
        )

        data class Sivilstand(
            val type: String?, //f.eks. SKILT
            val gyldigFraOgMed: String?, // dager siden epoch
            val relatertVedSivilstand: String?, // fnr
            val bekreftelsesdato: String?, // ?
        )

        data class UtflyttingFraNorge(
            val tilflyttingsland: String?, // F.eks. XUK
            val tilflyttingsstedIUtlandet: String?, // ??
            val utflyttingsdato: String?, // dager siden epoch
        )

        data class Bostedsadresse(
            val angittFlyttedato: String?, // dager siden epoch
            val gyldigFraOgMed: String?, // dager siden epoch
            val gyldigTilOgMed: String?, // dager siden epoch
            val coAdressenavn: String?, // ?
            val vegadresse: Vegadresse?,
            val matrikkeladresse: Matrikkeladresse?,
            val utenlandskAdresse: UtenlandskAdresse?,
            val ukjentBosted: UkjentBosted?,
        ) {
            data class UkjentBosted(
                val bostedskommune: String?, // F.eks. 0301
            )
        }

        data class Vegadresse(
            val matrikkelId: String?, // F.eks 9 siffer
            val husnummer: String?, // F.eks. 1
            val husbokstav: String?, // F.eks. B
            val bruksenhetsnummer: String?, // F.eks. 0101
            val adressenavn: String?, // F.eks. Storgata
            val kommunenummer: String?, // F.eks. 0301
            val bydelsnummer: String?, // F.eks. 030103
            val tilleggsnavn: String?, // F.eks. 1
            val postnummer: String?, // F.eks. 0001
            val koordinater: Koordinater?,
        )

        data class Matrikkeladresse(
            val matrikkelId: String?, // F.eks 9 siffer
            val bruksenhetsnummer: String?, // F.eks. 0101
            val tilleggsnavn: String?, // F.eks. HVALSMOEN
            val postnummer: String?, // F.eks. 0001
            val kommunenummer: String?, // F.eks. 0301
            val koordinater: Koordinater?,
        )

        data class UtenlandskAdresse(
            val adressenavnNummer: String?, // F.eks. 1
            val bygningEtasjeLeilighet: String?, // F.eks. 1
            val postboksNummerNavn: String?, // F.eks. 1
            val postkode: String?, // F.eks. 0001
            val bySted: String?, // F.eks. LONDON
            val regionDistriktOmraade: String?, // F.eks. tom string
            val landkode: String?, // F.eks. FR
        )

        data class Koordinater(
            val x: Long?, // F.eks 6 siffer
            val y: Long?, // F.eks 7 siffer
            val z: Long?, // F.eks. 0
        )

        data class Kontaktadresse(
            val gyldigFraOgMed: String?, // dager siden epoch
            val gyldigTilOgMed: String?, // dager siden epoch
            val type: String?, // F.eks. Utland
            val coAdressenavn: String?, // ?
            val postboksadresse: Postboksadresse?,
            val vegadresse: Vegadresse?,
            val postadresseIFrittFormat: String?, // ??
            val utenlandskAdresse: UtenlandskAdresse?,
            val utenlandsAdresseIFrittFormat: String?, /// ??
        )

        data class Postboksadresse(
            val postbokseier: String?, // F.eks. NAV
            val postboks: String?, // F.eks. 0001
            val postnummer: String?, // F.eks. 0001
        )
    }

    fun toDomain(): Either<KunneIkkeMappePersonhendelse.IkkeAktuellOpplysningstype,Personhendelse.IkkeTilknyttetSak> {
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
                utflyttingsdato = data.utflyttingFraNorge?.utflyttingsdato?.fraPersonhendelseDatoTilLocalDate()
            )

            else -> return KunneIkkeMappePersonhendelse.IkkeAktuellOpplysningstype(
                hendelseId = hendelseId,
                opplysningstype = opplysningstype,
            ).left()
        }
        return Personhendelse.IkkeTilknyttetSak(
            endringstype = when(data.endringstype) {
                "OPPRETTET" -> Personhendelse.Endringstype.OPPRETTET
                "KORRIGERT" -> Personhendelse.Endringstype.KORRIGERT
                "ANNULLERT" -> Personhendelse.Endringstype.ANNULLERT
                "OPPHOERT" -> Personhendelse.Endringstype.OPPHØRT
                else -> throw IllegalArgumentException("Personhendelse: Ukjent endringstype: ${data.endringstype} for hendelsesid $hendelseId, partisjon $partition og offset $offset")
            },
            hendelse = hendelse,
            metadata = Personhendelse.Metadata(
                hendelseId = hendelseId,
                personidenter =data.personidenter.toNonEmptyList(),
                tidligereHendelseId = data.tidligereHendelseId,
                offset = offset,
                partisjon = partition,
                master = data.master,
                key = "Innlest fra fil uten key. _id: $id",
            )
        ).right()
    }
}

fun String.fraPersonhendelseDatoTilLocalDate(): LocalDate {
    return LocalDate.EPOCH.plusDays(this.toLong())
}
