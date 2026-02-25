package no.nav.su.se.bakover.kontrollsamtale.domain

import no.nav.su.se.bakover.common.domain.job.JobContext
import no.nav.su.se.bakover.common.domain.job.NameAndLocalDateId
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class UtløptFristForKontrollsamtaleContext(
    private val id: NameAndLocalDateId,
    private val opprettet: Tidspunkt,
    private val endret: Tidspunkt,
    private val prosessert: Set<UUID>,
    private val ikkeMøtt: Set<UUID>,
    private val feilet: Set<Feilet>,
) : JobContext {
    constructor(
        clock: Clock,
        id: NameAndLocalDateId,
        opprettet: Tidspunkt = Tidspunkt.now(clock),
        endret: Tidspunkt = opprettet,
        prosessert: Set<UUID> = emptySet(),
        ikkeMøtt: Set<UUID> = emptySet(),
        feilet: Set<Feilet> = emptySet(),
    ) : this(id, opprettet, endret, prosessert, ikkeMøtt, feilet)

    override fun id(): NameAndLocalDateId {
        return id
    }

    fun opprettet(): Tidspunkt {
        return opprettet
    }

    fun endret(): Tidspunkt {
        return endret
    }

    fun prosessert(id: UUID, clock: Clock): UtløptFristForKontrollsamtaleContext {
        val fjernHvisFeilet = feilet.find { it.id == id }?.let { feilet.minus(it) } ?: feilet
        return copy(prosessert = prosessert + id, feilet = fjernHvisFeilet, endret = Tidspunkt.now(clock))
    }

    fun prosessert(): Set<UUID> {
        return prosessert
    }

    fun ikkeMøtt(id: UUID, clock: Clock): UtløptFristForKontrollsamtaleContext {
        val fjernHvisFeilet = feilet.find { it.id == id }?.let { feilet.minus(it) } ?: feilet
        return copy(
            prosessert = prosessert + id,
            ikkeMøtt = ikkeMøtt + id,
            feilet = fjernHvisFeilet,
            endret = Tidspunkt.now(clock),
        )
    }

    fun ikkeMøtt(): Set<UUID> {
        return ikkeMøtt
    }

    fun feilet(id: UUID, feil: String, clock: Clock): UtløptFristForKontrollsamtaleContext {
        return feilet.find { it.id == id }?.let {
            copy(feilet = feilet.minus(it) + it.retried(feil), endret = Tidspunkt.now(clock))
        } ?: copy(feilet = feilet + Feilet(id, 0, feil, null), endret = Tidspunkt.now(clock))
    }

    fun retryLimitReached(id: UUID): Boolean {
        return feilet.find { it.id == id }?.let { it.retries >= MAX_RETRIES } ?: false
    }

    fun prosessertMedFeil(id: UUID, clock: Clock, oppgaveId: OppgaveId): UtløptFristForKontrollsamtaleContext {
        return feilet.find { it.id == id }!!.let {
            copy(
                prosessert = prosessert + id,
                feilet = feilet.minus(it) + it.copy(oppgaveId = oppgaveId.toString()),
                endret = Tidspunkt.now(clock),
            )
        }
    }

    fun møtt(): Set<UUID> {
        return prosessert() - ikkeMøtt() - feilet().map { it.id }.toSet()
    }

    fun feilet(): Set<Feilet> {
        return feilet
    }

    data class Feilet(
        val id: UUID,
        val retries: Int,
        val feil: String,
        val oppgaveId: String?,
    ) {
        fun retried(feil: String): Feilet {
            return copy(retries = retries + 1, feil = feil)
        }
    }

    fun uprosesserte(utestående: Collection<UUID>): Set<UUID> {
        return utestående.toSet().minus(prosessert())
    }

    fun oppsummering(clock: Clock): String {
        return """
            ${"\n"}
            ***********************************
            Oppsummering av jobb: ${id.name}, tidspunkt:${Tidspunkt.now(clock)},
            Frist utløpt: ${id.date},
            Opprettet: $opprettet,
            Endret: $endret,
            Prosessert: $prosessert,
            Møtt: ${møtt()},
            IkkeMøtt: $ikkeMøtt,
            Feilet: $feilet
            ***********************************
            ${"\n"}
        """.trimIndent()
    }

    companion object {
        const val MAX_RETRIES = 2

        fun genererId(fristUtløpDato: LocalDate): NameAndLocalDateId {
            return NameAndLocalDateId(
                name = "HåndterUtløptFristForKontrollsamtale",
                date = fristUtløpDato,
            )
        }
    }
}
