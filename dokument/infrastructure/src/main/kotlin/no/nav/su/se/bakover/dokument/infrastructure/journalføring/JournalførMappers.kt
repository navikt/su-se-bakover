package no.nav.su.se.bakover.dokument.infrastructure.journalføring

import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstema
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import person.domain.Person

fun søkersNavn(navn: Person.Navn): String {
    return """${navn.etternavn}, ${navn.fornavn} ${navn.mellomnavn ?: ""}""".trimEnd()
}

fun Sakstype.tilBehandlingstema(): String {
    return when (this) {
        // TODO jah: Serialiserer vha. domenemodell.Bør flytte Behandlingstema.SU_ALDER.value og Behandlingstema.SU_UFØRE_FLYKTNING.value til common:infrastructure
        Sakstype.ALDER -> Behandlingstema.SU_ALDER.value
        Sakstype.UFØRE -> Behandlingstema.SU_UFØRE_FLYKTNING.value
    }
}

fun Fnr.tilBruker(): Bruker {
    return Bruker(id = this.toString())
}
