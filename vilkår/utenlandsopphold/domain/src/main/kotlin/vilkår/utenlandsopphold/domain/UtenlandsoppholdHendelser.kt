package vilkår.utenlandsopphold.domain

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import vilkår.utenlandsopphold.domain.annuller.AnnullerUtenlandsoppholdCommand
import vilkår.utenlandsopphold.domain.annuller.AnnullerUtenlandsoppholdHendelse
import vilkår.utenlandsopphold.domain.annuller.KunneIkkeAnnullereUtenlandsopphold
import vilkår.utenlandsopphold.domain.korriger.KorrigerUtenlandsoppholdCommand
import vilkår.utenlandsopphold.domain.korriger.KorrigerUtenlandsoppholdHendelse
import vilkår.utenlandsopphold.domain.korriger.KunneIkkeKorrigereUtenlandsopphold
import vilkår.utenlandsopphold.domain.registrer.KunneIkkeRegistereUtenlandsopphold
import vilkår.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdCommand
import vilkår.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdHendelse
import java.time.Clock
import java.util.UUID
import vilkår.utenlandsopphold.domain.annuller.apply as applyAnnuller
import vilkår.utenlandsopphold.domain.korriger.apply as applyKorriger

/**
 * Alle utenlandsoppholdhendelser som er registrert på en sak.
 */
data class UtenlandsoppholdHendelser private constructor(
    private val sakId: UUID,
    private val sorterteHendelser: List<UtenlandsoppholdHendelse>,
    private val clock: Clock,
) : List<UtenlandsoppholdHendelse> by sorterteHendelser {

    init {
        require(sorterteHendelser.sorted() == sorterteHendelser) {
            "UtenlandsoppholdHendelser må være sortert etter stigende versjon."
        }
        require(sorterteHendelser.distinctBy { it.hendelseId } == sorterteHendelser) {
            "UtenlandsoppholdHendelser kan ikke ha duplikat hendelseId."
        }
        require(sorterteHendelser.distinctBy { it.versjon } == sorterteHendelser) {
            "UtenlandsoppholdHendelser kan ikke ha duplikat versjon."
        }
        sorterteHendelser.mapNotNull { it.tidligereHendelseId }.let {
            require(it.distinct() == it) {
                "En hendelse kan kun bli korrigert/annullert en gang. Oppdaget duplikate tidligereHendelseId: ${
                    it.groupBy { it }.filter { it.value.size > 1 }.values
                }"
            }
        }
        require(sorterteHendelser.map { it.sakId }.distinct().size <= 1) {
            "UtenlandsoppholdHendelser kan kun være knyttet til én sak, men var: ${
                sorterteHendelser.map { it.sakId }.distinct()
            }"
        }
        require(sorterteHendelser.map { it.entitetId }.distinct().size <= 1) {
            "UtenlandsoppholdHendelser kan kun være knyttet til én enitetId (samme som sakId), men var: ${
                sorterteHendelser.map { it.entitetId }.distinct()
            }"
        }
    }

    val currentState: RegistrerteUtenlandsopphold by lazy {
        if (sorterteHendelser.isEmpty()) {
            RegistrerteUtenlandsopphold.empty(sakId)
        } else {
            toCurrentState(sakId, sorterteHendelser.toNonEmptyList())
        }
    }

    private val aktive by lazy {
        currentState.filterNot { it.erAnnullert }
    }

    /**
     * Tillatter at innreisedag og utreisedag faller på samme dag.
     */
    private fun List<RegistrertUtenlandsopphold>.overlapper(periode: DatoIntervall): Boolean {
        return this.any { it.periode overlapperExcludingEndDate periode }
    }

    fun registrer(
        command: RegistrerUtenlandsoppholdCommand,
        nesteVersjon: Hendelsesversjon,
    ): Either<KunneIkkeRegistereUtenlandsopphold, UtenlandsoppholdHendelser> {
        if (aktive.overlapper(command.periode)) {
            return KunneIkkeRegistereUtenlandsopphold.OverlappendePeriode.left()
        }
        val hendelse = command.toHendelse(
            nesteVersjon = nesteVersjon,
            clock = clock,
        )
        return create(sakId, clock, this + hendelse).right()
    }

    fun korriger(
        command: KorrigerUtenlandsoppholdCommand,
        nesteVersjon: Hendelsesversjon,
    ): Either<KunneIkkeKorrigereUtenlandsopphold, UtenlandsoppholdHendelser> {
        if (
            aktive
                .filterNot { it.versjon == command.korrigererVersjon }
                .overlapper(command.periode)
        ) {
            return KunneIkkeKorrigereUtenlandsopphold.OverlappendePeriode.left()
        }
        val hendelse = command.toHendelse(
            nesteVersjon = nesteVersjon,
            clock = clock,
            korrigererHendelse = fraVersjon(command.korrigererVersjon)!!,
        )
        return create(sakId, clock, this + hendelse).right()
    }

    fun annuller(
        command: AnnullerUtenlandsoppholdCommand,
        nesteVersjon: Hendelsesversjon,
    ): Either<KunneIkkeAnnullereUtenlandsopphold, UtenlandsoppholdHendelser> {
        val hendelse = command.toHendelse(
            nesteVersjon = nesteVersjon,
            clock = clock,
            annullererHendelse = fraVersjon(command.annullererVersjon)!!,
        )
        return create(sakId, clock, this + hendelse).right()
    }

    private fun fraVersjon(annullererVersjon: Hendelsesversjon): UtenlandsoppholdHendelse? {
        return sorterteHendelser.singleOrNull { it.versjon == annullererVersjon }
    }

    companion object {

        fun empty(sakId: UUID, clock: Clock): UtenlandsoppholdHendelser {
            return UtenlandsoppholdHendelser(
                sakId = sakId,
                clock = clock,
                sorterteHendelser = listOf(),
            )
        }

        fun create(
            sakId: UUID,
            clock: Clock,
            hendelser: List<UtenlandsoppholdHendelse>,
        ): UtenlandsoppholdHendelser {
            return UtenlandsoppholdHendelser(
                sakId = sakId,
                clock = clock,
                sorterteHendelser = hendelser.sorted(),
            )
        }

        private fun toCurrentState(
            sakId: UUID,
            hendelser: NonEmptyList<UtenlandsoppholdHendelse>,
        ): RegistrerteUtenlandsopphold {
            return hendelser.fold(mapOf<HendelseId, RegistrertUtenlandsopphold>()) { acc, hendelse ->
                val hendelseId = hendelse.hendelseId
                when (hendelse) {
                    is RegistrerUtenlandsoppholdHendelse -> acc.plus(hendelseId to hendelse.toRegistrertUtenlandsopphold())
                    is KorrigerUtenlandsoppholdHendelse -> acc.plus(
                        hendelseId to acc[hendelse.tidligereHendelseId]!!.applyKorriger(
                            hendelse,
                        ),
                    ).minus(hendelse.tidligereHendelseId)

                    is AnnullerUtenlandsoppholdHendelse -> acc.plus(
                        hendelseId to acc[hendelse.tidligereHendelseId]!!.applyAnnuller(
                            hendelse,
                        ),
                    ).minus(hendelse.tidligereHendelseId)

                    else -> throw IllegalStateException("Ukjent type: ${hendelse::class.simpleName}")
                }
            }.values.toList().sortedBy { it.versjon }.let {
                RegistrerteUtenlandsopphold(
                    sakId = sakId,
                    utenlandsopphold = it,
                )
            }
        }
    }
}
