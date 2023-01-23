package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.person.pdl.leesah.Endringstype
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.orNull
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.person.SivilstandTyper
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.web.services.personhendelser.KunneIkkeMappePersonhendelse.IkkeAktuellOpplysningstype
import org.apache.kafka.clients.consumer.ConsumerRecord
import no.nav.person.pdl.leesah.Personhendelse as EksternPersonhendelse

internal object PersonhendelseMapper {
    private enum class Opplysningstype(val value: String) {
        DØDSFALL("DOEDSFALL_V1"),
        UTFLYTTING_FRA_NORGE("UTFLYTTING_FRA_NORGE"),
        SIVILSTAND("SIVILSTAND_V1"),
        BOSTEDSADRESSE("BOSTEDSADRESSE_V1"),
        KONTAKTADRESSE("KONTAKTADRESSE_V1"),
        ;
        // https://github.com/navikt/pdl/blob/master/libs/contract-pdl-avro/src/main/java/no/nav/person/pdl/leesah/Opplysningstype.java
    }

    internal fun map(
        message: ConsumerRecord<String, EksternPersonhendelse>,
    ): Either<KunneIkkeMappePersonhendelse, Personhendelse.IkkeTilknyttetSak> {
        val personhendelse: EksternPersonhendelse = message.value()

        return when (personhendelse.getOpplysningstype()) {
            Opplysningstype.DØDSFALL.value -> {
                Personhendelse.Hendelse.Dødsfall(
                    dødsdato = personhendelse.getDoedsfall().flatMap {
                        it.getDoedsdato()
                    }.orNull(),
                ).right()
            }

            Opplysningstype.UTFLYTTING_FRA_NORGE.value -> {
                Personhendelse.Hendelse.UtflyttingFraNorge(
                    utflyttingsdato = personhendelse.getUtflyttingFraNorge().flatMap {
                        it.getUtflyttingsdato()
                    }.orNull(),
                ).right()
            }

            Opplysningstype.SIVILSTAND.value -> {
                (
                    personhendelse.getSivilstand().map {
                        Personhendelse.Hendelse.Sivilstand(
                            type = when (it.getType()) {
                                "UOPPGITT" -> SivilstandTyper.UOPPGITT
                                "UGIFT" -> SivilstandTyper.UGIFT
                                "GIFT" -> SivilstandTyper.GIFT
                                "ENKE_ELLER_ENKEMANN" -> SivilstandTyper.ENKE_ELLER_ENKEMANN
                                "SKILT" -> SivilstandTyper.SKILT
                                "SEPARERT" -> SivilstandTyper.SEPARERT
                                "REGISTRERT_PARTNER" -> SivilstandTyper.REGISTRERT_PARTNER
                                "SEPARERT_PARTNER" -> SivilstandTyper.SEPARERT_PARTNER
                                "SKILT_PARTNER" -> SivilstandTyper.SKILT_PARTNER
                                "GJENLEVENDE_PARTNER" -> SivilstandTyper.GJENLEVENDE_PARTNER
                                null -> null
                                else -> throw IllegalArgumentException("Personhendelse: Ukjent sivilstandstype: ${it.getType()} for hendelsesid ${personhendelse.getHendelseId()}, partisjon ${message.partition()} og offset ${message.offset()}")
                            },
                            gyldigFraOgMed = it.getGyldigFraOgMed().orNull(),
                            relatertVedSivilstand = it.getRelatertVedSivilstand().map { fnr ->
                                Fnr(fnr)
                            }.orNull(),
                            bekreftelsesdato = it.getBekreftelsesdato().orNull(),
                        )
                    }.orNull() ?: Personhendelse.Hendelse.Sivilstand.EMPTY
                    ).right()
            }

            Opplysningstype.BOSTEDSADRESSE.value -> Personhendelse.Hendelse.Bostedsadresse.right()
            Opplysningstype.KONTAKTADRESSE.value -> Personhendelse.Hendelse.Kontaktadresse.right()
            else -> {
                IkkeAktuellOpplysningstype(personhendelse.getHendelseId(), personhendelse.getOpplysningstype()).left()
            }
        }.map { hendelse: Personhendelse.Hendelse ->
            Personhendelse.IkkeTilknyttetSak(
                endringstype = hentEndringstype(personhendelse.getEndringstype()),
                hendelse = hendelse,
                metadata = Personhendelse.Metadata(
                    personidenter = personhendelse.getPersonidenter().toNonEmptyList(),
                    hendelseId = personhendelse.getHendelseId(),
                    tidligereHendelseId = personhendelse.getTidligereHendelseId()
                        .let { if (it.isPresent) it.get() else null },
                    offset = message.offset(),
                    partisjon = message.partition(),
                    master = personhendelse.getMaster(),
                    key = message.key().removeUnicodeNullcharacter(),
                ),
            )
        }
    }

    private fun hentEndringstype(endringstype: Endringstype) =
        when (endringstype) {
            Endringstype.OPPRETTET -> Personhendelse.Endringstype.OPPRETTET
            Endringstype.KORRIGERT -> Personhendelse.Endringstype.KORRIGERT
            Endringstype.ANNULLERT -> Personhendelse.Endringstype.ANNULLERT
            Endringstype.OPPHOERT -> Personhendelse.Endringstype.OPPHØRT
        }
}

internal sealed class KunneIkkeMappePersonhendelse {
    data class IkkeAktuellOpplysningstype(
        val hendelseId: String,
        val opplysningstype: String,
    ) : KunneIkkeMappePersonhendelse()
}

/**
 * PDL avro-serialiserer key-strengen (fødselsnummer eller aktørId) som prepender den med en null-byte.
 * Dette smeller i postgres.
 * https://en.wikipedia.org/wiki/Null_character
 */
internal fun String.removeUnicodeNullcharacter(): String {
    return this
        .replace("\u0000", "")
        .replace("\\u0000", "")
}
