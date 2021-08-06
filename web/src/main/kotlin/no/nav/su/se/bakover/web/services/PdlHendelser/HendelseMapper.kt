package no.nav.su.se.bakover.web.services.PdlHendelser

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.hendelse.PdlHendelse
import no.nav.su.se.bakover.web.services.PdlHendelser.HendelseMapperException.IkkeAktuellOpplysningstype
import org.apache.kafka.clients.consumer.ConsumerRecord

internal object HendelseMapper {
    internal enum class Opplysningstype(val value: String) {
        DØDSFALL("DOEDSFALL_V1"),
        UTFLYTTING_FRA_NORGE("UTFLYTTING_FRA_NORGE");
    }

    internal fun map(
        message: ConsumerRecord<String, Personhendelse>,
    ): Either<HendelseMapperException, PdlHendelse.Ny> {
        val key: String = message.key()
        val offset: Long = message.offset()
        val personhendelse: Personhendelse = message.value()

        val aktørId = hentAktørId(personhendelse, key).getOrHandle {
            return it.left()
        }

        return when (personhendelse.getOpplysningstype()) {
            Opplysningstype.DØDSFALL.value -> {
                val dødsdato = personhendelse.getDoedsfall().get().let {
                    if (it.getDoedsdato().isPresent) it.getDoedsdato().get() else null
                }

                PdlHendelse.Ny(
                    hendelseId = personhendelse.getHendelseId(),
                    offset = offset,
                    gjeldendeAktørId = aktørId,
                    endringstype = hentEndringstype(personhendelse.getEndringstype()),
                    personidenter = personhendelse.getPersonidenter(),
                    hendelse = PdlHendelse.Hendelse.Dødsfall(dødsdato = dødsdato),
                ).right()
            }

            Opplysningstype.UTFLYTTING_FRA_NORGE.value -> {
                val utflyttetDato = personhendelse.getUtflyttingFraNorge().get().let {
                    if (it.getUtflyttingsdato().isPresent) it.getUtflyttingsdato().get() else null
                }

                PdlHendelse.Ny(
                    hendelseId = personhendelse.getHendelseId(),
                    offset = offset,
                    gjeldendeAktørId = aktørId,
                    endringstype = hentEndringstype(personhendelse.getEndringstype()),
                    personidenter = personhendelse.getPersonidenter(),
                    hendelse = PdlHendelse.Hendelse.UtflyttingFraNorge(utflyttingsdato = utflyttetDato),
                ).right()
            }
            else -> {
                IkkeAktuellOpplysningstype.left()
            }
        }
    }

    private fun hentAktørId(
        personhendelse: Personhendelse,
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
            Endringstype.OPPRETTET -> PdlHendelse.Endringstype.OPPRETTET
            Endringstype.KORRIGERT -> PdlHendelse.Endringstype.KORRIGERT
            Endringstype.ANNULLERT -> PdlHendelse.Endringstype.ANNULLERT
            Endringstype.OPPHOERT -> PdlHendelse.Endringstype.OPPHOERT
        }
}

internal sealed class HendelseMapperException {
    object IkkeAktuellOpplysningstype : HendelseMapperException()
    object KunneIkkeHenteAktørId : HendelseMapperException()
}
