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
        UTFLYTTING_FRA_NORGE("UTFLYTTING_FRA_NORGE");
    }

    internal fun map(
        message: ConsumerRecord<String, EksternPersonhendelse>,
    ): Either<HendelseMapperException, Personhendelse.Ny> {
        val key: String = message.key()
        val offset: Long = message.offset()
        val personhendelse: EksternPersonhendelse = message.value()

        val aktørId = hentAktørId(personhendelse, key).getOrHandle {
            return it.left()
        }

        return when (personhendelse.getOpplysningstype()) {
            Opplysningstype.DØDSFALL.value -> {
                val dødsdato = personhendelse.getDoedsfall().get().let {
                    if (it.getDoedsdato().isPresent) it.getDoedsdato().get() else null
                }

                Personhendelse.Ny(
                    hendelseId = personhendelse.getHendelseId(),
                    offset = offset,
                    gjeldendeAktørId = aktørId,
                    endringstype = hentEndringstype(personhendelse.getEndringstype()),
                    personidenter = personhendelse.getPersonidenter(),
                    hendelse = Personhendelse.Hendelse.Dødsfall(dødsdato = dødsdato),
                ).right()
            }

            Opplysningstype.UTFLYTTING_FRA_NORGE.value -> {
                val utflyttetDato = personhendelse.getUtflyttingFraNorge().get().let {
                    if (it.getUtflyttingsdato().isPresent) it.getUtflyttingsdato().get() else null
                }

                Personhendelse.Ny(
                    hendelseId = personhendelse.getHendelseId(),
                    offset = offset,
                    gjeldendeAktørId = aktørId,
                    endringstype = hentEndringstype(personhendelse.getEndringstype()),
                    personidenter = personhendelse.getPersonidenter(),
                    hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(utflyttingsdato = utflyttetDato),
                ).right()
            }
            else -> {
                IkkeAktuellOpplysningstype.left()
            }
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
            Endringstype.OPPHOERT -> Personhendelse.Endringstype.OPPHOERT
        }
}

internal sealed class HendelseMapperException {
    object IkkeAktuellOpplysningstype : HendelseMapperException()
    object KunneIkkeHenteAktørId : HendelseMapperException()
}
