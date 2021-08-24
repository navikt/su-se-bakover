package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.person.pdl.leesah.Endringstype
import no.nav.su.se.bakover.common.orNull
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.domain.person.SivilstandTyper
import no.nav.su.se.bakover.web.services.personhendelser.KunneIkkeMappePersonhendelse.IkkeAktuellOpplysningstype
import org.apache.kafka.clients.consumer.ConsumerRecord
import no.nav.person.pdl.leesah.Personhendelse as EksternPersonhendelse

internal object PersonhendelseMapper {
    private enum class Opplysningstype(val value: String) {
        DØDSFALL("DOEDSFALL_V1"),
        UTFLYTTING_FRA_NORGE("UTFLYTTING_FRA_NORGE"),
        SIVILSTAND("SIVILSTAND_V1");
        // https://github.com/navikt/pdl/blob/b483f1fee4b73fa79b4ccff6e3e953f33c2bd2dc/libs/contract-pdl-avro/src/main/java/no/nav/person/pdl/leesah/Opplysningstype.java
    }

    internal fun map(
        message: ConsumerRecord<String, EksternPersonhendelse>,
    ): Either<KunneIkkeMappePersonhendelse, Personhendelse.Ny> {
        val key: String? = message.key()
        val personhendelse: EksternPersonhendelse = message.value()
        if (key == null) {
            // Vi har sett tilfeller av dette i preprod
            return KunneIkkeMappePersonhendelse.KunneIkkeHenteAktørId(
                personhendelse.getHendelseId(),
                personhendelse.getOpplysningstype(),
            ).left()
        }

        val aktørId = hentAktørId(personhendelse, key).getOrHandle {
            return it.left()
        }

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
                                "REGISTRERT_PARTNER" -> SivilstandTyper.REGISTRERT_PARTNER
                                "SEPARERT_PARTNER" -> SivilstandTyper.SEPARERT_PARTNER
                                "SKILT_PARTNER" -> SivilstandTyper.SKILT_PARTNER
                                "GJENLEVENDE_PARTNER" -> SivilstandTyper.GJENLEVENDE_PARTNER
                                null -> null
                                else -> throw IllegalArgumentException("Ukjent sivilstandstype i personhendelse: ${it.getType()}")
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
            else -> {
                IkkeAktuellOpplysningstype(personhendelse.getHendelseId(), personhendelse.getOpplysningstype()).left()
            }
        }.map { hendelse: Personhendelse.Hendelse ->
            Personhendelse.Ny(
                gjeldendeAktørId = aktørId,
                endringstype = hentEndringstype(personhendelse.getEndringstype()),
                personidenter = NonEmptyList.fromListUnsafe(personhendelse.getPersonidenter()),
                hendelse = hendelse,
                metadata = Personhendelse.Metadata(
                    hendelseId = personhendelse.getHendelseId(),
                    tidligereHendelseId = personhendelse.getTidligereHendelseId()
                        .let { if (it.isPresent) it.get() else null },
                    offset = message.offset(),
                    partisjon = message.partition(),
                    master = personhendelse.getMaster(),
                    key = message.key(),
                ),
            )
        }
    }

    private fun hentAktørId(
        personhendelse: EksternPersonhendelse,
        key: String,
    ): Either<KunneIkkeMappePersonhendelse.KunneIkkeHenteAktørId, AktørId> {
        val id = personhendelse.getPersonidenter().firstOrNull { key.contains(it) }
            ?: return KunneIkkeMappePersonhendelse.KunneIkkeHenteAktørId(
                personhendelse.getHendelseId(),
                personhendelse.getOpplysningstype(),
            ).left()

        return AktørId(id).right()
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

    data class KunneIkkeHenteAktørId(
        val hendelseId: String,
        val opplysningstype: String,
    ) : KunneIkkeMappePersonhendelse()
}
