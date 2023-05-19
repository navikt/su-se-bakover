package no.nav.su.se.bakover.common.audit

import no.nav.su.se.bakover.common.person.Fnr
import java.util.UUID

data class AuditLogEvent(
    val navIdent: String,
    val berørtBrukerId: Fnr,
    val action: Action,
    val behandlingId: UUID?,
    val callId: String?,
) {
    /**
     * Hva slags CRUD-operasjon blir gjort
     */
    enum class Action(val value: String) {
        /** Bruker har sett data. */
        ACCESS("audit:access"),

        /** Bruker har endret data */
        UPDATE("audit:update"),

        /** Bruker har lagt inn nye data */
        CREATE("audit:create"),

        /** Minimalt innsyn, f.eks. ved visning i liste. */
        SEARCH("audit:search"),
    }
}
