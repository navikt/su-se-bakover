package økonomi.infrastructure.kvittering.persistence

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import økonomi.domain.kvittering.NyKvitteringHendelse

/**
 * Uprosessert kvittering fra Oppdrag.
 * Vi gjør ingen knytninger mot sak eller utbetaling her.
 *
 * Dette er en litt spesiell hendelse hvor vi i praksis ikke har noen entitetId og alltid har versjon 1.
 * Dvs. alle kvitteringer er unike og kan ikke sammenlignes med hverandre.
 */
internal data class NyKvitteringHendelseJson(
    val rawXml: String,
) {
    companion object {
        fun NyKvitteringHendelse.toJson(): String {
            return NyKvitteringHendelseJson(
                rawXml = this.rawXml,
            ).let {
                serialize(it)
            }
        }

        fun PersistertHendelse.toNyKvitteringHendelse(): NyKvitteringHendelse {
            require(this.sakId == null) {
                "Uprosessert utbetalingskvittering skal ikke ha sakId, men var $sakId"
            }
            require(this.tidligereHendelseId == null) {
                "Uprosessert utbetalingskvittering skal ikke ha tidligereHendelseId, men var $tidligereHendelseId"
            }
            return deserialize<NyKvitteringHendelseJson>(this.data).let { json ->
                NyKvitteringHendelse.fraPersistert(
                    hendelseId = HendelseId.fromUUID(this.hendelseId),
                    hendelsestidspunkt = this.hendelsestidspunkt,
                    hendelseMetadata = this.hendelseMetadata,
                    forrigeVersjon = this.versjon,
                    entitetId = this.entitetId,
                    rawXml = json.rawXml,
                )
            }
        }
    }
}
