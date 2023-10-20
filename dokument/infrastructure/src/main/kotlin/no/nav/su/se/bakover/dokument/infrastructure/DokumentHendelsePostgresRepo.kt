package no.nav.su.se.bakover.dokument.infrastructure

import dokument.domain.hendelser.DokumentHendelse
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.hendelser.GenerertDokumentForArkiveringHendelse
import dokument.domain.hendelser.GenerertDokumentForJournalføringHendelse
import dokument.domain.hendelser.GenerertDokumentForUtsendelseHendelse
import dokument.domain.hendelser.JournalførtDokumentForArkivering
import dokument.domain.hendelser.JournalførtDokumentForArkiveringHendelse
import dokument.domain.hendelser.JournalførtDokumentForUtsendelse
import dokument.domain.hendelser.JournalførtDokumentForUtsendelseHendelse
import dokument.domain.hendelser.LagretDokument
import dokument.domain.hendelser.LagretDokumentForJournalføring
import dokument.domain.hendelser.LagretDokumentForUtsendelse
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.dokument.infrastructure.GenerertDokumentHendelseDbJson.Companion.toDbJson
import no.nav.su.se.bakover.dokument.infrastructure.JournalførtDokumentHendelseDbJson.Companion.dataDbJson
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelseFilPostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import java.util.UUID

class DokumentHendelsePostgresRepo(
    private val hendelseRepo: HendelseRepo,
    private val hendelseFilRepo: HendelseFilPostgresRepo,
) : DokumentHendelseRepo {
    override fun lagre(hendelse: DokumentHendelse, sessionContext: SessionContext?) {
        lagreHendelse(hendelse, null, sessionContext)
    }

    override fun lagre(hendelse: DokumentHendelse, hendelseFil: HendelseFil, sessionContext: SessionContext?) {
        lagreHendelse(hendelse, hendelseFil, sessionContext)
    }

    private fun lagreHendelse(hendelse: DokumentHendelse, hendelseFil: HendelseFil?, sessionContext: SessionContext?) {
        (hendelseRepo as HendelsePostgresRepo).persisterSakshendelse(
            hendelse = hendelse,
            type = when (hendelse) {
                is GenerertDokumentForJournalføringHendelse -> LagretDokumentForJournalføring
                is GenerertDokumentForUtsendelseHendelse -> LagretDokumentForUtsendelse
                is GenerertDokumentForArkiveringHendelse -> LagretDokument
                is JournalførtDokumentForArkiveringHendelse -> JournalførtDokumentForArkivering
                is JournalførtDokumentForUtsendelseHendelse -> JournalførtDokumentForUtsendelse
            },
            data = when (hendelse) {
                is JournalførtDokumentForArkiveringHendelse -> hendelse.dataDbJson(hendelse.relaterteHendelser)
                is JournalførtDokumentForUtsendelseHendelse -> hendelse.dataDbJson(hendelse.relaterteHendelser)
                is GenerertDokumentForArkiveringHendelse -> hendelse.dokumentUtenFil.toDbJson(hendelse.relaterteHendelser)
                is GenerertDokumentForJournalføringHendelse -> hendelse.dokumentUtenFil.toDbJson(hendelse.relaterteHendelser)
                is GenerertDokumentForUtsendelseHendelse -> hendelse.dokumentUtenFil.toDbJson(hendelse.relaterteHendelser)
            },
            sessionContext = sessionContext,
        )
        hendelseFil?.let {
            hendelseFilRepo.lagre(
                sakId = hendelse.sakId,
                hendelseFil = it,
                sessionContext = sessionContext,
            )
        }
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

    override fun hentHendelse(hendelseId: HendelseId, sessionContext: SessionContext?): DokumentHendelse? {
        return (hendelseRepo as HendelsePostgresRepo)
            .hentHendelseForHendelseId(hendelseId, sessionContext)?.toDokumentHendelse()
    }

    override fun hentFilFor(hendelseId: HendelseId, sessionContext: SessionContext?): HendelseFil? {
        return hendelseFilRepo.hentFor(hendelseId, sessionContext)
    }

    override fun hentHendelseOgFilFor(
        hendelseId: HendelseId,
        sessionContext: SessionContext?,
    ): Pair<DokumentHendelse?, HendelseFil?> {
        return Pair(hentHendelse(hendelseId, sessionContext), hentFilFor(hendelseId, sessionContext))
    }
}

private fun PersistertHendelse.toDokumentHendelse(): DokumentHendelse {
    return when (this.type) {
        LagretDokument, LagretDokumentForJournalføring, LagretDokumentForUtsendelse -> GenerertDokumentHendelseDbJson.toDomain(
            type = this.type,
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
        )

        JournalførtDokumentForArkivering, JournalførtDokumentForUtsendelse -> JournalførtDokumentHendelseDbJson.toDomain(
            type = type,
            data = data,
            hendelseId = hendelseId,
            sakId = sakId!!,
            hendelsestidspunkt = hendelsestidspunkt,
            versjon = versjon,
            meta = this.defaultHendelseMetadata(),
        )

        else -> throw IllegalStateException("Ugyldig type for dokument hendelse. type var $type")
    }
}
