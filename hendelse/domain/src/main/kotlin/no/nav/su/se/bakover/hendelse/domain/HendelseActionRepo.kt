package no.nav.su.se.bakover.hendelse.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface HendelseActionRepo {
    fun lagre(hendelser: List<HendelseId>, action: String, context: SessionContext)
    fun lagre(hendelseId: HendelseId, action: String, context: SessionContext)

    fun hentSakOgHendelsesIderSomIkkeHarKjørtAction(
        action: String,
        hendelsestype: String,
        sx: SessionContext? = null,
        limit: Int = 10,
    ): Map<UUID, List<HendelseId>>

    fun hentHendelseIderForActionOgType(
        action: String,
        hendelsestype: String,
        sx: SessionContext? = null,
        limit: Int = 10,
    ): List<HendelseId>
}
