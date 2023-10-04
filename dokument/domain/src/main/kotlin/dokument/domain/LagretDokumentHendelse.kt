package dokument.domain.dokument.domain

import dokument.domain.Dokument
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.util.UUID

/**
 * Ved iverksettelse av behandlinger, sender vi som regel et brev (enkeltvedtak eller informasjon).
 * Denne hendelsen dekker også tilfeller der vi journalfører dokumenter (som ikke blir sendt som brev).
 * I noen tilfeller sendes det ikke brev (saksbehandler velger dette. I disse tilfellene har bruker blitt informert allerede eller saksbehandler lager et samlevedtak. Regulering er et eksempel der vi ved vanlig løp ikke sender brev.).
 * I noen tilfeller sendes brevet rett etter iverksettelsen (ingen nye utbetalingslinjer).
 * Dersom vi har sendt noe til oppdrag venter vi på kvittering. (I førsteomgang brukes bare denne hendelsen i dette tilfellet)
 * Dersom simuleringa viste feilutbetaling, går vi inn i et tilbakekrevingsløp, da venter på på kravgrunnlaget før vi sender brevet.
 */
data class LagretDokumentHendelse(
    override val hendelseId: HendelseId,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: DefaultHendelseMetadata,
    override val sakId: UUID,
    val dokument: Dokument.MedMetadata,
) : Sakshendelse {

    // Vi har ingen mulighet for å korrigere/annullere denne hendelsen atm.
    override val tidligereHendelseId: HendelseId? = null

    override val entitetId: UUID = sakId

    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        fun fraPersistert(
            hendelseId: HendelseId,
            hendelsestidspunkt: Tidspunkt,
            hendelseMetadata: DefaultHendelseMetadata,
            entitetId: UUID,
            forrigeVersjon: Hendelsesversjon,
            sakId: UUID,
            dokument: Dokument.MedMetadata,
        ): LagretDokumentHendelse {
            return LagretDokumentHendelse(
                hendelseId = hendelseId,
                hendelsestidspunkt = hendelsestidspunkt,
                meta = hendelseMetadata,
                sakId = sakId,
                versjon = forrigeVersjon,
                dokument = dokument,
            ).also {
                require(it.entitetId == entitetId) {
                    "Den persistert entitetId var ulik den utleda fra domenet:${it.entitetId} vs. $entitetId. "
                }
            }
        }
    }
}
