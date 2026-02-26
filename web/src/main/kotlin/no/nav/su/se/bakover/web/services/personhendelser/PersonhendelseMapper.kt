package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.person.pdl.leesah.Endringstype
import no.nav.su.se.bakover.common.domain.extensions.orNull
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.toTidspunkt
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.web.services.personhendelser.KunneIkkeMappePersonhendelse.IkkeAktuellOpplysningstype
import org.apache.kafka.clients.consumer.ConsumerRecord
import person.domain.SivilstandTyper
import no.nav.person.pdl.leesah.Personhendelse as EksternPersonhendelse

internal data object PersonhendelseMapper {
    internal fun map(
        message: ConsumerRecord<String, EksternPersonhendelse>,
    ): Either<KunneIkkeMappePersonhendelse, Personhendelse.IkkeTilknyttetSak> {
        val personhendelse: EksternPersonhendelse = message.value()

        return when (personhendelse.getOpplysningstype()) {
            OpplysningstypeForPersonhendelse.DØDSFALL.value -> {
                Personhendelse.Hendelse.Dødsfall(
                    dødsdato = personhendelse.getDoedsfall().flatMap {
                        it.getDoedsdato()
                    }.orNull(),
                ).right()
            }

            OpplysningstypeForPersonhendelse.UTFLYTTING_FRA_NORGE.value -> {
                Personhendelse.Hendelse.UtflyttingFraNorge(
                    utflyttingsdato = personhendelse.getUtflyttingFraNorge().flatMap {
                        it.getUtflyttingsdato()
                    }.orNull(),
                ).right()
            }

            OpplysningstypeForPersonhendelse.SIVILSTAND.value -> {
                (
                    personhendelse.getSivilstand().map {
                        val type: String? = it.type
                        Personhendelse.Hendelse.Sivilstand(
                            type = SivilstandTyper.fromString(type).getOrElse {
                                throw IllegalArgumentException("Personhendelse: Ukjent sivilstandstype: ${it.value} for hendelsesid ${personhendelse.hendelseId}, partisjon ${message.partition()} og offset ${message.offset()}")
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

            OpplysningstypeForPersonhendelse.BOSTEDSADRESSE.value -> {
                (
                    personhendelse.getBostedsadresse().map {
                        Personhendelse.Hendelse.Bostedsadresse(
                            angittFlyttedato = it.getAngittFlyttedato().orNull(),
                            gyldigFraOgMed = it.getGyldigFraOgMed().orNull(),
                            gyldigTilOgMed = it.getGyldigTilOgMed().orNull(),
                            coAdressenavn = it.getCoAdressenavn().orNull(),
                            adressetype = when {
                                it.getVegadresse().isPresent -> Personhendelse.Hendelse.Bostedsadresse.Adressetype.VEGADRESSE
                                it.getMatrikkeladresse().isPresent -> Personhendelse.Hendelse.Bostedsadresse.Adressetype.MATRIKKELADRESSE
                                it.getUtenlandskAdresse().isPresent -> Personhendelse.Hendelse.Bostedsadresse.Adressetype.UTENLANDSK_ADRESSE
                                it.getUkjentBosted().isPresent -> Personhendelse.Hendelse.Bostedsadresse.Adressetype.UKJENT_BOSTED
                                else -> null
                            },
                        )
                    }.orNull() ?: Personhendelse.Hendelse.Bostedsadresse.EMPTY
                    ).right()
            }

            OpplysningstypeForPersonhendelse.KONTAKTADRESSE.value -> {
                (
                    personhendelse.getKontaktadresse().map {
                        Personhendelse.Hendelse.Kontaktadresse(
                            gyldigFraOgMed = it.getGyldigFraOgMed().orNull(),
                            gyldigTilOgMed = it.getGyldigTilOgMed().orNull(),
                            type = it.type,
                            coAdressenavn = it.getCoAdressenavn().orNull(),
                            adressetype = when {
                                it.getPostboksadresse().isPresent -> Personhendelse.Hendelse.Kontaktadresse.Adressetype.POSTBOKSADRESSE
                                it.getVegadresse().isPresent -> Personhendelse.Hendelse.Kontaktadresse.Adressetype.VEGADRESSE
                                it.getPostadresseIFrittFormat().isPresent -> Personhendelse.Hendelse.Kontaktadresse.Adressetype.POSTADRESSE_I_FRITT_FORMAT
                                it.getUtenlandskAdresse().isPresent -> Personhendelse.Hendelse.Kontaktadresse.Adressetype.UTENLANDSK_ADRESSE
                                it.getUtenlandskAdresseIFrittFormat().isPresent -> Personhendelse.Hendelse.Kontaktadresse.Adressetype.UTENLANDSK_ADRESSE_I_FRITT_FORMAT
                                else -> null
                            },
                        )
                    }.orNull() ?: Personhendelse.Hendelse.Kontaktadresse.EMPTY
                    ).right()
            }
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
                    key = message.key().removeUnwantedJsonCharacters(),
                    eksternOpprettet = personhendelse.opprettet.toTidspunkt(),
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

sealed interface KunneIkkeMappePersonhendelse {
    data class IkkeAktuellOpplysningstype(
        val hendelseId: String,
        val opplysningstype: String,
    ) : KunneIkkeMappePersonhendelse
}
