package no.nav.su.se.bakover.domain.personhendelse

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import person.domain.SivilstandTyper
import java.time.LocalDate
import java.util.UUID

sealed interface Personhendelse {

    enum class Endringstype {
        OPPRETTET,
        KORRIGERT,
        ANNULLERT,
        OPPHØRT,
    }

    val endringstype: Endringstype
    val hendelse: Hendelse
    val metadata: Metadata

    data class IkkeTilknyttetSak(
        override val endringstype: Endringstype,
        override val hendelse: Hendelse,
        override val metadata: Metadata,
    ) : Personhendelse {
        fun tilknyttSak(id: UUID, sakIdSaksnummerFnr: SakInfo, opprettet: Tidspunkt) =
            TilknyttetSak.IkkeSendtTilOppgave(
                endringstype = endringstype,
                hendelse = hendelse,
                id = id,
                sakId = sakIdSaksnummerFnr.sakId,
                saksnummer = sakIdSaksnummerFnr.saksnummer,
                metadata = metadata,
                antallFeiledeForsøk = 0,
                opprettet = opprettet,
            )
    }

    sealed interface TilknyttetSak : Personhendelse {
        val id: UUID
        val sakId: UUID
        val saksnummer: Saksnummer
        val antallFeiledeForsøk: Int
        val opprettet: Tidspunkt

        data class IkkeSendtTilOppgave(
            override val endringstype: Endringstype,
            override val hendelse: Hendelse,
            override val id: UUID,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val metadata: Metadata,
            override val antallFeiledeForsøk: Int,
            override val opprettet: Tidspunkt,
        ) : TilknyttetSak {
            fun tilSendtTilOppgave(oppgaveId: OppgaveId) =
                SendtTilOppgave(
                    endringstype = endringstype,
                    hendelse = hendelse,
                    id = id,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    oppgaveId = oppgaveId,
                    metadata = metadata,
                    antallFeiledeForsøk = antallFeiledeForsøk,
                    opprettet = opprettet,
                )
        }

        data class SendtTilOppgave(
            override val endringstype: Endringstype,
            override val hendelse: Hendelse,
            override val id: UUID,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val metadata: Metadata,
            val oppgaveId: OppgaveId,
            override val antallFeiledeForsøk: Int,
            override val opprettet: Tidspunkt,
        ) : TilknyttetSak
    }

    sealed interface Hendelse {
        /**
         * @see <a href="https://navikt.github.io/pdl/#_d%C3%B8dsfall">Dokumentasjonen</a>
         * */
        data class Dødsfall(val dødsdato: LocalDate?) : Hendelse {
            companion object {
                val EMPTY = Dødsfall(null)
            }
        }

        /**
         * @see <a href="https://navikt.github.io/pdl/#_utflytting">Dokumentasjonen</a>
         * */
        data class UtflyttingFraNorge(val utflyttingsdato: LocalDate?) : Hendelse {
            companion object {
                val EMPTY = UtflyttingFraNorge(null)
            }
        }

        /**
         * @see <a href="https://navikt.github.io/pdl/#_sivilstand">Dokumentasjonen</a>
         * @see [person.domain.Person.Sivilstand]
         * */
        data class Sivilstand(
            val type: SivilstandTyper?,
            val gyldigFraOgMed: LocalDate?,
            val relatertVedSivilstand: Fnr?,
            val bekreftelsesdato: LocalDate?,
        ) : Hendelse {
            companion object {
                val EMPTY = Sivilstand(null, null, null, null)
            }

            override fun toString(): String {
                return "Sivilstand(type=$type, gyldigFraOgMed=$gyldigFraOgMed, relatertVedSivilstand=${relatertVedSivilstand.let { "****" }}, bekreftelsesdato=$bekreftelsesdato)"
            }
        }

        /**
         * @see <a href="https://pdldocs-navno.msappproxy.net/ekstern/index.html#opplysningstyper-adresser-bostedsAdresse">Dokumentasjonen</a>
         * */
        data object Bostedsadresse : Hendelse

        /**
         * @see <a href="https://pdldocs-navno.msappproxy.net/ekstern/index.html#opplysningstyper-adresser-kontaktAdresse">Dokumentasjonen</a>
         * */
        data object Kontaktadresse : Hendelse
    }

    /** Metadata rundt hendelsen
     * Et offset er kun unikt kombinert med partisjonen (direkte tilknyttet Kafka)
     * */
    data class Metadata(
        val hendelseId: String,
        val personidenter: NonEmptyList<String>,
        val tidligereHendelseId: String?,
        val offset: Long,
        val partisjon: Int,
        /** f.eks. FREG */
        val master: String,
        /** Kafkameldinger kommer som key-value pairs (keyen inneholder aktørid) */
        val key: String,
        val eksternOpprettet: Tidspunkt?,
    )
}
