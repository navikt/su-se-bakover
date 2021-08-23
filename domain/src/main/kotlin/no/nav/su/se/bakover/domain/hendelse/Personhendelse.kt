package no.nav.su.se.bakover.domain.hendelse

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.SivilstandTyper
import java.time.LocalDate
import java.util.UUID

sealed class Personhendelse {
    enum class Endringstype {
        OPPRETTET,
        KORRIGERT,
        ANNULLERT,
        OPPHØRT,
    }

    abstract val gjeldendeAktørId: AktørId
    abstract val endringstype: Endringstype
    abstract val hendelse: Hendelse

    data class Ny(
        override val gjeldendeAktørId: AktørId,
        override val endringstype: Endringstype,
        override val hendelse: Hendelse,
        val personidenter: NonEmptyList<String>,
        val metadata: Metadata,
    ) : Personhendelse()

    data class TilknyttetSak(
        val id: UUID,
        override val gjeldendeAktørId: AktørId,
        override val endringstype: Endringstype,
        override val hendelse: Hendelse,
        val saksnummer: Saksnummer,
        val sakId: UUID,
        val oppgaveId: OppgaveId?,
    ) : Personhendelse()

    sealed class Hendelse {
        /**
         * @see <a href="https://navikt.github.io/pdl/#_d%C3%B8dsfall">Dokumentasjonen</a>
         * */
        data class Dødsfall(val dødsdato: LocalDate?) : Hendelse()

        /**
         * @see <a href="https://navikt.github.io/pdl/#_utflytting">Dokumentasjonen</a>
         * */
        data class UtflyttingFraNorge(val utflyttingsdato: LocalDate?) : Hendelse()

        /**
         * @see <a href="https://navikt.github.io/pdl/#_sivilstand">Dokumentasjonen</a>
         * @see [no.nav.su.se.bakover.domain.Person.Sivilstand]
         * */
        data class Sivilstand(
            val type: SivilstandTyper,
            val gyldigFraOgMed: LocalDate?,
            val relatertVedSivilstand: Fnr?,
            val bekreftelsesdato: LocalDate?,
        ) : Hendelse()
    }

    /** Metadata rundt hendelsen
     * Et offset er kun unikt kombinert med partisjonen (direkte tilknyttet Kafka)
     * */
    data class Metadata(
        val hendelseId: String,
        val tidligereHendelseId: String?,
        val offset: Long,
        val partisjon: Int,
        /** f.eks. FREG */
        val master: String,
        /** Kafkameldinger kommer som key-value pairs (keyen inneholder aktørid) */
        val key: String,
    )
}
