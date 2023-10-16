package no.nav.su.se.bakover.dokument.infrastructure

import dokument.domain.DokumentHendelseRepo
import dokument.domain.LagretDokumentHendelse
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.dokument.infrastructure.LagretDokumentHendelseDbJson.Companion.toDbJson
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import java.lang.IllegalStateException
import java.util.UUID

// mulig vi må skille på hva dokumenter er
val LagretDokument = Hendelsestype("LAGRET_DOKUMENT")

class DokumentHendelsePostgresRepo(
    private val hendelseRepo: HendelseRepo,
) : DokumentHendelseRepo {
    override fun lagre(hendelse: LagretDokumentHendelse, sessionContext: SessionContext) {
        (hendelseRepo as HendelsePostgresRepo).persisterSakshendelse(
            hendelse = hendelse,
            type = LagretDokument,
            data = hendelse.dokument.toDbJson(hendelse.relaterteHendelser),
            sessionContext = sessionContext,
        )
    }

    override fun hentForSak(sakId: UUID, sessionContext: SessionContext): List<LagretDokumentHendelse> {
        return (hendelseRepo as HendelsePostgresRepo)
            .hentHendelserForSakIdOgType(
                sakId = sakId,
                type = LagretDokument,
                sessionContext = sessionContext,
            ).map {
                it.toLagretDokumentHendelse()
            }
    }
}

private fun PersistertHendelse.toLagretDokumentHendelse(): LagretDokumentHendelse {
    return when (this.type) {
        LagretDokument -> LagretDokumentHendelseDbJson.toDomain(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
        )

        else -> throw IllegalStateException("Ugyldig type for lagret dokument hendelse. type var $type")
    }
}
