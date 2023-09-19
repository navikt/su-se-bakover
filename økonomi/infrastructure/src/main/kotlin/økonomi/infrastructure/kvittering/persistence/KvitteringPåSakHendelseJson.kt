package økonomi.infrastructure.kvittering.persistence

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.kvittering.KvitteringPåSakHendelse

internal data class KvitteringPåSakHendelseJson(
    val utbetalingsstatus: String,
    val originalKvittering: String,
    val utbetalingId: UUID30,
) {
    val utbetalingsstatusDomain: Kvittering.Utbetalingsstatus by lazy {
        when (utbetalingsstatus) {
            "OK" -> Kvittering.Utbetalingsstatus.OK
            "OK_MED_VARSEL" -> Kvittering.Utbetalingsstatus.OK_MED_VARSEL
            "FEIL" -> Kvittering.Utbetalingsstatus.FEIL
            else -> throw IllegalArgumentException("Ukjent utbetalingsstatus $this")
        }
    }

    companion object {
        fun KvitteringPåSakHendelse.toJson(): String {
            fun Kvittering.Utbetalingsstatus.toDbString(): String {
                return when (this) {
                    Kvittering.Utbetalingsstatus.OK -> "OK"
                    Kvittering.Utbetalingsstatus.OK_MED_VARSEL -> "OK_MED_VARSEL"
                    Kvittering.Utbetalingsstatus.FEIL -> "FEIL"
                }
            }
            return KvitteringPåSakHendelseJson(
                utbetalingsstatus = this.utbetalingsstatus.toDbString(),
                originalKvittering = this.originalKvittering,
                utbetalingId = this.utbetalingId,
            ).let {
                serialize(it)
            }
        }

        fun PersistertHendelse.toKvitteringPåSakHendelse(): KvitteringPåSakHendelse {
            require(this.tidligereHendelseId == null) {
                "KvitteringPåSakHendelse skal ikke ha tidligereHendelseId, men var $tidligereHendelseId"
            }
            return deserialize<KvitteringPåSakHendelseJson>(this.data).let { json ->
                KvitteringPåSakHendelse.fraPersistert(
                    hendelseId = this.hendelseId,
                    hendelsestidspunkt = this.hendelsestidspunkt,
                    hendelseMetadata = this.hendelseMetadata,
                    forrigeVersjon = this.versjon,
                    entitetId = this.entitetId,
                    utbetalingsstatus = json.utbetalingsstatusDomain,
                    originalKvittering = json.originalKvittering,
                    utbetalingId = json.utbetalingId,
                    sakId = sakId!!,
                )
            }
        }
    }
}
