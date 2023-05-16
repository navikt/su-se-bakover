package no.nav.su.se.bakover.domain.skatt

import no.nav.su.se.bakover.common.persistence.SessionContext

interface DokumentSkattRepo {
    fun lagre(dok: Skattedokument)
    fun lagre(dok: Skattedokument, txc: SessionContext)
}
