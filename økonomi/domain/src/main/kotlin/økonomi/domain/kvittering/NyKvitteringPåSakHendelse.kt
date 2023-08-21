package økonomi.domain.kvittering

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.util.UUID

/**
 * Vi knytter en kvittering mot en sak og en utbetaling.
 *
 *
 */
data class NyKvitteringPåSakHendelse(
    override val hendelseId: HendelseId,
    override val versjon: Hendelsesversjon,
    override val triggetAv: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val meta: HendelseMetadata,
    val kvittering: Kvittering,
    val rawXml: String,
) : Sakshendelse {
    init {
        require(versjon.value > 1L) {
            "Bare en liten guard mot å opprette en hendelse med versjon 1, siden den er reservert for SakOpprettetHendelse"
        }
    }

    override val tidligereHendelseId: HendelseId? = null
    override val entitetId: UUID = sakId

    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId)
        return this.versjon.compareTo(other.versjon)
    }
}
