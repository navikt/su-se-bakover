package no.nav.su.se.bakover.hendelse.domain

import arrow.core.Nel
import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface HendelsekonsumenterRepo {
    fun lagre(
        hendelser: List<HendelseId>,
        konsumentId: HendelseskonsumentId,
        context: SessionContext? = null,
    )
    fun lagre(
        hendelseId: HendelseId,
        konsumentId: HendelseskonsumentId,
        context: SessionContext? = null,
    )

    /**
     * Brukes for de hendelsene som har sakId
     */
    fun hentUteståendeSakOgHendelsesIderForKonsumentOgType(
        konsumentId: HendelseskonsumentId,
        hendelsestype: Hendelsestype,
        sx: SessionContext? = null,
        limit: Int = 10,
    ): Map<UUID, Nel<HendelseId>>

    /**
     * Brukes for de hendelsene som ikke har sakId
     *
     * @return et sett med unike hendelseIder
     */
    fun hentHendelseIderForKonsumentOgType(
        konsumentId: HendelseskonsumentId,
        hendelsestype: Hendelsestype,
        sx: SessionContext? = null,
        limit: Int = 10,
    ): Set<HendelseId>
}
