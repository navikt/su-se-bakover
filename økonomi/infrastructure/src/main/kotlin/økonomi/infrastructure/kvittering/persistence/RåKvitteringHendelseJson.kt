package økonomi.infrastructure.kvittering.persistence

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import økonomi.domain.kvittering.RåUtbetalingskvitteringhendelse

private data class RåKvitteringHendelseJson(
    val originalKvittering: String,
)

internal fun RåUtbetalingskvitteringhendelse.toJson(): String {
    return RåKvitteringHendelseJson(
        originalKvittering = this.originalKvittering,
    ).let {
        serialize(it)
    }
}

internal fun PersistertHendelse.toRåKvitteringHendelse(): RåUtbetalingskvitteringhendelse {
    require(this.sakId == null) {
        "Uprosessert utbetalingskvittering skal ikke ha sakId, men var $sakId"
    }
    require(this.tidligereHendelseId == null) {
        "Uprosessert utbetalingskvittering skal ikke ha tidligereHendelseId, men var $tidligereHendelseId"
    }
    return deserialize<RåKvitteringHendelseJson>(this.data).let { json ->
        RåUtbetalingskvitteringhendelse.fraPersistert(
            hendelseId = this.hendelseId,
            hendelsestidspunkt = this.hendelsestidspunkt,
            forrigeVersjon = this.versjon,
            entitetId = this.entitetId,
            originalKvittering = json.originalKvittering,
        )
    }
}
