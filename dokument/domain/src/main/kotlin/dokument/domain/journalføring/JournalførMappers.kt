package dokument.domain.journalføring

import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstema
import no.nav.su.se.bakover.common.domain.sak.Sakstype

fun Sakstype.tilBehandlingstema(): String {
    return when (this) {
        // TODO jah: Serialiserer vha. domenemodell.Bør flytte Behandlingstema.SU_ALDER.value og Behandlingstema.SU_UFØRE_FLYKTNING.value til common:infrastructure
        Sakstype.ALDER -> Behandlingstema.SU_ALDER.value
        Sakstype.UFØRE -> Behandlingstema.SU_UFØRE_FLYKTNING.value
    }
}
