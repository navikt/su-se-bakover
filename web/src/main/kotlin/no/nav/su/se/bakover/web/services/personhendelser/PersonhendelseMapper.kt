package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.person.pdl.leesah.Endringstype
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.web.services.personhendelser.HendelseMapperException.IkkeAktuellOpplysningstype
import org.apache.kafka.clients.consumer.ConsumerRecord
import no.nav.person.pdl.leesah.Personhendelse as EksternPersonhendelse

internal object HendelseMapper {
    internal enum class Opplysningstype(val value: String) {
        DØDSFALL("DOEDSFALL_V1"),
        UTFLYTTING_FRA_NORGE("UTFLYTTING_FRA_NORGE"),
        SIVILSTAND("SIVILSTAND_V1");
        // https://github.com/navikt/pdl/blob/b483f1fee4b73fa79b4ccff6e3e953f33c2bd2dc/libs/contract-pdl-avro/src/main/java/no/nav/person/pdl/leesah/Opplysningstype.java
    }

    internal fun map(
        message: ConsumerRecord<String, EksternPersonhendelse>,
    ): Either<HendelseMapperException, Personhendelse.Ny> {
        val key: String = message.key()
        val personhendelse: EksternPersonhendelse = message.value()

        val aktørId = hentAktørId(personhendelse, key).getOrHandle {
            return it.left()
        }

        return when (personhendelse.getOpplysningstype()) {
            Opplysningstype.DØDSFALL.value -> {
                val dødsdato = personhendelse.getDoedsfall().get().let {
                    if (it.getDoedsdato().isPresent) it.getDoedsdato().get() else null
                }
                Personhendelse.Hendelse.Dødsfall(dødsdato = dødsdato).right()
            }
            Opplysningstype.UTFLYTTING_FRA_NORGE.value -> {
                val utflyttetDato = personhendelse.getUtflyttingFraNorge().get().let {
                    if (it.getUtflyttingsdato().isPresent) it.getUtflyttingsdato().get() else null
                }
                Personhendelse.Hendelse.UtflyttingFraNorge(utflyttingsdato = utflyttetDato).right()
            }
            else -> {
                IkkeAktuellOpplysningstype.left()
            }
        }.map { hendelse ->
            Personhendelse.Ny(
                gjeldendeAktørId = aktørId,
                endringstype = hentEndringstype(personhendelse.getEndringstype()),
                personidenter = personhendelse.getPersonidenter(),
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
    ): Either<HendelseMapperException.KunneIkkeHenteAktørId, AktørId> {
        val id = key.substring(6) // Nyckeln på Kafka-meldinger fra Pdl er aktør-id prepend:et med 6 rare tegn.
        val idFinnesSomPersonident = personhendelse.getPersonidenter().any { it == id }

        if (!idFinnesSomPersonident) {
            return HendelseMapperException.KunneIkkeHenteAktørId.left()
        }

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

internal sealed class HendelseMapperException {
    object IkkeAktuellOpplysningstype : HendelseMapperException()
    object KunneIkkeHenteAktørId : HendelseMapperException()
}
