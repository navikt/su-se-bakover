package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.lang.IllegalStateException
import java.util.UUID

data class EksternInstitusjonsoppholdHendelse(
    val hendelseId: Long,
    val oppholdId: Long,
    val norskident: Fnr,
    val type: InstitusjonsoppholdType,
    val kilde: InstitusjonsoppholdKilde,
)

sealed interface InstitusjonsoppholdHendelse {
    val id: UUID
    val opprettet: Tidspunkt
    val eksternHendelse: EksternInstitusjonsoppholdHendelse
    fun knyttTilSak(sakId: UUID): KnyttetTilSak

    data class IkkeKnyttetTilSak(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val eksternHendelse: EksternInstitusjonsoppholdHendelse,
    ) : InstitusjonsoppholdHendelse {
        override fun knyttTilSak(sakId: UUID): KnyttetTilSak =
            KnyttetTilSak.UtenOppgaveId(sakId = sakId, ikkeKnyttetTilSak = this)
    }

    sealed interface KnyttetTilSak : InstitusjonsoppholdHendelse {
        val sakId: UUID
        val oppgaveId: OppgaveId?

        fun knyttTilOppgaveId(oppgaveId: OppgaveId): MedOppgaveId

        data class UtenOppgaveId(
            override val sakId: UUID,
            val ikkeKnyttetTilSak: IkkeKnyttetTilSak,
        ) : KnyttetTilSak, InstitusjonsoppholdHendelse by ikkeKnyttetTilSak {
            override val oppgaveId = null

            override fun knyttTilSak(sakId: UUID): KnyttetTilSak =
                throw IllegalStateException("Kan ikke knytte til en annen sakId")

            override fun knyttTilOppgaveId(oppgaveId: OppgaveId): MedOppgaveId =
                MedOppgaveId(utenOppgaveId = this, oppgaveId = oppgaveId)
        }

        data class MedOppgaveId(
            val utenOppgaveId: UtenOppgaveId,
            override val oppgaveId: OppgaveId,
        ) : KnyttetTilSak by utenOppgaveId {
            override fun knyttTilSak(sakId: UUID): KnyttetTilSak =
                throw IllegalStateException("Kan ikke knytte til en annen sakId")

            override fun knyttTilOppgaveId(oppgaveId: OppgaveId) =
                throw IllegalStateException("Kan ikke knytte til en annen oppgaveId")
        }
    }
}
