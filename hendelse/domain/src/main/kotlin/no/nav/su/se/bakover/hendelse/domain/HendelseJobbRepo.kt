package no.nav.su.se.bakover.hendelse.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface HendelseJobbRepo {
    fun lagre(hendelser: List<HendelseId>, jobbNavn: String, context: SessionContext)
    fun lagre(hendelseId: HendelseId, jobbNavn: String, context: SessionContext)
    fun hentSakIdOgHendelseIderForNavnOgType(
        jobbNavn: String,
        hendelsestype: String,
        sx: SessionContext? = null,
        limit: Int = 10,
    ): Map<UUID, List<HendelseId>>

    fun hentHendelseIderForNavnOgType(
        jobbNavn: String,
        hendelsestype: String,
        sx: SessionContext? = null,
        limit: Int = 10,
    ): List<HendelseId>
}
