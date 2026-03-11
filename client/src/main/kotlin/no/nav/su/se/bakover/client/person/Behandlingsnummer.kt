package no.nav.su.se.bakover.client.person

import no.nav.su.se.bakover.common.domain.sak.Sakstype

/**
 * https://behandlingskatalog.nais.adeo.no/process/purpose/SUPPLERENDESTOENAD/bb50b682-633c-4e4c-aae0-99c4c95c264a
 */
enum class Behandlingsnummer(val value: String) {
    ALDER("B237"),
    UFØRE("B126"),
    ;

    companion object {
        fun fraSakstype(sakstype: Sakstype): Behandlingsnummer {
            return when (sakstype) {
                Sakstype.ALDER -> ALDER
                Sakstype.UFØRE -> UFØRE
            }
        }
    }
}
