package no.nav.su.se.bakover.hendelse.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface HendelseRepo {
    fun hentSisteVersjonFraEntitetId(entitetId: UUID, sessionContext: SessionContext = defaultSessionContext()): Hendelsesversjon?
    fun defaultSessionContext(): SessionContext
}
