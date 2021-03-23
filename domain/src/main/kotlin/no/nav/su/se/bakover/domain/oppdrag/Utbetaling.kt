package no.nav.su.se.bakover.domain.oppdrag

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.time.LocalDate
import java.util.UUID

interface IOppdragMetadata {
    val id: UUID30
    val opprettet: Tidspunkt
    val sakId: UUID
    val saksnummer: Saksnummer
    val fnr: Fnr
    val type: Utbetaling.UtbetalingsType
    val behandler: NavIdentBruker
    val avstemmingsnøkkel: Avstemmingsnøkkel
}

data class OppdragMetadata(
    override val id: UUID30 = UUID30.randomUUID(),
    override val opprettet: Tidspunkt = Tidspunkt.now(),
    override val sakId: UUID,
    override val saksnummer: Saksnummer,
    override val fnr: Fnr,
    override val type: Utbetaling.UtbetalingsType,
    override val behandler: NavIdentBruker,
    override val avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
) : IOppdragMetadata

@JsonIgnoreProperties("metadata")
interface Oppdragsmelding {
    val metadata: OppdragMetadata
}

@JsonIgnoreProperties("metadata")
interface SimulertOppdragsmelding : Oppdragsmelding {
    val simulering: Simulering
}

@JsonIgnoreProperties("metadata")
interface OversendtOppdragsmelding : SimulertOppdragsmelding {
    val utbetalingsrequest: Utbetalingsrequest
}

@JsonIgnoreProperties("metadata")
interface KvittertOppdragsmelding : OversendtOppdragsmelding {
    val kvittering: Kvittering
}

sealed class Melding(
    override val metadata: OppdragMetadata
) : Oppdragsmelding, IOppdragMetadata by metadata

sealed class Opphør(
    metadata: OppdragMetadata
) : Melding(metadata) {
    abstract val fraOgMed: LocalDate

    data class ForSimulering(
        override val metadata: OppdragMetadata,
        override val fraOgMed: LocalDate,
    ) : Opphør(metadata) {
        fun toSimulertOpphør(simulering: Simulering) = Simulert(
            metadata = metadata,
            fraOgMed = fraOgMed,
            simulering = simulering
        )
    }

    data class Simulert(
        override val metadata: OppdragMetadata,
        override val fraOgMed: LocalDate,
        override val simulering: Simulering,
    ) : Opphør(metadata), SimulertOppdragsmelding

    data class Oversendt(
        override val metadata: OppdragMetadata,
        override val fraOgMed: LocalDate,
        override val simulering: Simulering,
        override val utbetalingsrequest: Utbetalingsrequest,
    ) : Opphør(metadata), OversendtOppdragsmelding

    data class Kvittert(
        override val metadata: OppdragMetadata,
        override val fraOgMed: LocalDate,
        override val simulering: Simulering,
        override val utbetalingsrequest: Utbetalingsrequest,
        override val kvittering: Kvittering,
    ) : Opphør(metadata), KvittertOppdragsmelding
}

sealed class Utbetaling(
    metadata: OppdragMetadata
) : Melding(metadata) {
    abstract val utbetalingslinjer: List<Utbetalingslinje>

    fun sisteUtbetalingslinje() = utbetalingslinjer.lastOrNull()
    fun erFørstegangsUtbetaling() = utbetalingslinjer.any { it.forrigeUtbetalingslinjeId == null }

    fun tidligsteDato() = utbetalingslinjer.minByOrNull { it.fraOgMed }!!.fraOgMed
    fun senesteDato() = utbetalingslinjer.maxByOrNull { it.tilOgMed }!!.tilOgMed
    fun bruttoBeløp() = utbetalingslinjer.sumBy { it.beløp }

    data class UtbetalingForSimulering(
        override val metadata: OppdragMetadata,
        override val utbetalingslinjer: List<Utbetalingslinje>,
    ) : Utbetaling(metadata) {
        fun toSimulertUtbetaling(simulering: Simulering) =
            SimulertUtbetaling(
                metadata = metadata,
                utbetalingslinjer = utbetalingslinjer,
                simulering = simulering
            )
    }

    data class SimulertUtbetaling(
        override val metadata: OppdragMetadata,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val simulering: Simulering,
    ) : Utbetaling(metadata), SimulertOppdragsmelding {
        fun toOversendtUtbetaling(oppdragsmelding: Utbetalingsrequest) =
            OversendtUtbetaling.UtenKvittering(
                metadata = metadata,
                utbetalingslinjer = utbetalingslinjer,
                simulering = simulering,
                utbetalingsrequest = oppdragsmelding
            )
    }

    sealed class OversendtUtbetaling(
        metadata: OppdragMetadata
    ) : Utbetaling(metadata) {
        data class UtenKvittering(
            override val metadata: OppdragMetadata,
            override val utbetalingslinjer: List<Utbetalingslinje>,
            override val simulering: Simulering,
            override val utbetalingsrequest: Utbetalingsrequest
        ) : OversendtUtbetaling(metadata), OversendtOppdragsmelding {
            fun toKvittertUtbetaling(kvittering: Kvittering) =
                MedKvittering(
                    metadata = metadata,
                    utbetalingslinjer = utbetalingslinjer,
                    simulering = simulering,
                    utbetalingsrequest = utbetalingsrequest,
                    kvittering = kvittering
                )
        }

        data class MedKvittering(
            override val metadata: OppdragMetadata,
            override val utbetalingslinjer: List<Utbetalingslinje>,
            override val simulering: Simulering,
            override val utbetalingsrequest: Utbetalingsrequest,
            override val kvittering: Kvittering
        ) : OversendtUtbetaling(metadata), KvittertOppdragsmelding {
            fun kvittertMedFeilEllerVarsel() =
                listOf(
                    Kvittering.Utbetalingsstatus.OK_MED_VARSEL,
                    Kvittering.Utbetalingsstatus.FEIL
                ).contains(kvittering.utbetalingsstatus)
        }
    }

    enum class UtbetalingsType {
        NY,
        STANS,
        GJENOPPTA,
        OPPHØR
    }

    companion object {
        /**
         * Returnerer utbetalingene sortert økende etter tidspunktet de er sendt til oppdrag. Filtrer bort de som er kvittert feil.
         * TODO jah: Ved initialisering e.l. gjør en faktisk verifikasjon på at ref-verdier på utbetalingslinjene har riktig rekkefølge
         */
        fun List<Melding>.hentOversendteUtbetalingerUtenFeil(): List<Melding> =
            this.filter { it is OversendtOppdragsmelding || it is KvittertOppdragsmelding && it.kvittering.erKvittertOk() }
                .sortedBy { it.opprettet.instant } // TODO potentially fix sorting
    }
}
