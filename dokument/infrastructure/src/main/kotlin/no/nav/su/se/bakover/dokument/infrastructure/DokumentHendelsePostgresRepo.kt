package no.nav.su.se.bakover.dokument.infrastructure

import dokument.domain.hendelser.DokumentHendelse
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.hendelser.LagretDokumentForJournalføringHendelse
import dokument.domain.hendelser.LagretDokumentForUtsendelseHendelse
import dokument.domain.hendelser.LagretDokumentHendelse
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.dokument.infrastructure.DokumentHendelseDbJson.Companion.toDbJson
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelseFilPostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import java.util.UUID

val LagretDokument = Hendelsestype("LAGRET_DOKUMENT")
val LagretDokumentForJournalføring = Hendelsestype("LAGRET_DOKUMENT_FOR_JOURNALFØRING")
val LagretDokumentForUtsendelse = Hendelsestype("LAGRET_DOKUMENT_FOR_UTSENDELSE")

class DokumentHendelsePostgresRepo(
    private val hendelseRepo: HendelseRepo,
    private val hendelseFilRepo: HendelseFilPostgresRepo,
) : DokumentHendelseRepo {
    override fun lagre(hendelse: DokumentHendelse, hendelseFil: HendelseFil, sessionContext: SessionContext?) {
        (hendelseRepo as HendelsePostgresRepo).persisterSakshendelse(
            hendelse = hendelse,
            type = when (hendelse) {
                is LagretDokumentForJournalføringHendelse -> LagretDokumentForJournalføring
                is LagretDokumentForUtsendelseHendelse -> LagretDokumentForUtsendelse
                is LagretDokumentHendelse -> LagretDokument
            },
            data = hendelse.dokumentUtenFil.toDbJson(hendelse.relaterteHendelser),
            sessionContext = sessionContext,
        )
        hendelseFilRepo.lagre(
            sakId = hendelse.sakId,
            hendelseFil = hendelseFil,
            sessionContext = sessionContext,
        )
    }

    override fun hentForSak(sakId: UUID, sessionContext: SessionContext): List<DokumentHendelse> {
        return (hendelseRepo as HendelsePostgresRepo).let { repo ->
            listOf(LagretDokument, LagretDokumentForJournalføring, LagretDokumentForUtsendelse).flatMap {
                repo.hentHendelserForSakIdOgType(
                    sakId = sakId,
                    type = LagretDokument,
                    sessionContext = sessionContext,
                ).map { it.toDokumentHendelse() }
            }
        }
    }
}

private fun PersistertHendelse.toDokumentHendelse(): DokumentHendelse {
    return when (this.type) {
        LagretDokument, LagretDokumentForJournalføring, LagretDokumentForUtsendelse -> DokumentHendelseDbJson.toDomain(
            type = this.type,
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
