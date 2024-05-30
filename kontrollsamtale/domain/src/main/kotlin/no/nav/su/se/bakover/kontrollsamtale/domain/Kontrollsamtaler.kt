package no.nav.su.se.bakover.kontrollsamtale.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.erLikEllerTilstøtende
import no.nav.su.se.bakover.kontrollsamtale.domain.opprett.KanIkkeOppretteKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.opprett.OpprettKontrollsamtaleCommand
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Garanterer at kontrolsamtaler er sortert etter opprettet-tidspunkt og er knyttet til samme sakId.
 * Garanterer også at alle kontrollsamtaler har unik id.
 */
data class Kontrollsamtaler(
    val sakId: UUID,
    val kontrollsamtaler: List<Kontrollsamtale>,
) : List<Kontrollsamtale> by kontrollsamtaler {

    private val log = LoggerFactory.getLogger(this::class.java)

    constructor(sakId: UUID, vararg kontrollsamtaler: Kontrollsamtale) : this(sakId, kontrollsamtaler.toList())

    val innkallingsdatoer: List<LocalDate> = kontrollsamtaler.map { it.innkallingsdato }
    val frister: List<LocalDate> = kontrollsamtaler.map { it.frist }

    init {
        require(kontrollsamtaler.all { it.sakId == sakId }) {
            "Alle kontrollsamtaler må ha samme sakId. Forventet $sakId, fikk ${kontrollsamtaler.map { it.sakId }}"
        }
        require(kontrollsamtaler.distinctBy { it.id }.size == kontrollsamtaler.size) {
            "Kontrollsamtaler må ha unik id, men fikk ${kontrollsamtaler.map { it.id }}"
        }
        kontrollsamtaler.map { it.opprettet.instant }.also {
            require(it == it.sorted()) { "Kontrollsamtaler må være sortert etter opprettet-tidspunkt: $it" }
        }
        // TODO jah: Hvis vi sjekker at databasen ikke har duplikate innkallingsdato, frist, dokumentId og journalpostId, kan vi legge inn garantier for dette her.
        // Dersom database-dataene ikke har sammenhengende innkallinger, er det også mulighet til å legge en garanti på.
    }

    companion object {
        fun empty(sakId: UUID) = Kontrollsamtaler(sakId, emptyList())
    }

    /**
     * Oppretter en ny, planlagt kontrollsamtale.
     * Frist - se [regnUtFristFraInnkallingsdato]
     *
     * @return [KanIkkeOppretteKontrollsamtale] hvis innkallingsmåned allerede er brukt, eller ikke er frem i tid.
     * @throws IllegalArgumentException hvis frist ikke er første dag i måneden.
     */
    fun opprettKontrollsamtale(
        command: OpprettKontrollsamtaleCommand,
        clock: Clock,
    ): Either<KanIkkeOppretteKontrollsamtale, Pair<Kontrollsamtale, Kontrollsamtaler>> {
        val fristerSomMåned = kontrollsamtaler
            .filter { it.status != Kontrollsamtalestatus.ANNULLERT }
            .map { Måned.fra(it.frist) }

        if (fristerSomMåned.erLikEllerTilstøtende(command.innkallingsmåned)) {
            log.info("Opprett kontrollsamtale: Innkallingsmåned kræsjer med eksisterende frister. Command: $command. Eksisterende frister: $fristerSomMåned")
            return KanIkkeOppretteKontrollsamtale.UgyldigInnkallingsmåned(command.innkallingsmåned).left()
        }
        val innkallingsdatoSomMåned = kontrollsamtaler
            .filter { it.status != Kontrollsamtalestatus.ANNULLERT }
            .map { Måned.fra(it.innkallingsdato) }

        if (innkallingsdatoSomMåned.erLikEllerTilstøtende(command.innkallingsmåned)) {
            log.info("Opprett kontrollsamtale: Innkallingsmåned kræsjer med eksisterende innkallinger. Command: $command. Eksisterende frister: $innkallingsdatoSomMåned")
            return KanIkkeOppretteKontrollsamtale.UgyldigInnkallingsmåned(command.innkallingsmåned).left()
        }
        if (!command.innkallingsmåned.etter(Måned.now(clock))) {
            return KanIkkeOppretteKontrollsamtale.InnkallingsmånedMåVæreEtterNåværendeMåned(
                command.innkallingsmåned,
            ).left()
        }
        val nyKontrollsamtale = Kontrollsamtale(
            opprettet = Tidspunkt.now(clock),
            sakId = command.sakId,
            innkallingsdato = command.innkallingsmåned.fraOgMed,
            status = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
            dokumentId = null,
            journalpostIdKontrollnotat = null,
        )
        return (nyKontrollsamtale to Kontrollsamtaler(sakId, kontrollsamtaler + nyKontrollsamtale)).right()
    }

    fun hentKontrollsamtale(kontrollsamtaleId: UUID): Kontrollsamtale? {
        return kontrollsamtaler.find { it.id == kontrollsamtaleId }
    }
}
