package dokument.domain.hendelser

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import java.util.UUID

val GenerertDokument = Hendelsestype("GENERERT_DOKUMENT")
val GenerertDokumentForJournalføring = Hendelsestype("GENERERT_DOKUMENT_FOR_JOURNALFØRING")
val GenerertDokumentForUtsendelse = Hendelsestype("GENERERT_DOKUMENT_FOR_UTSENDELSE")
val JournalførtDokumentForArkivering = Hendelsestype("JOURNALTFØRT_DOKUMENT_FOR_ARKIVERING")
val JournalførtDokumentForUtsendelse = Hendelsestype("JOURNALTFØRT_DOKUMENT_FOR_UTSENDELSE")

interface DokumentHendelseRepo {
    fun lagre(hendelse: DokumentHendelse, hendelseFil: HendelseFil, sessionContext: SessionContext? = null)
    fun lagre(hendelse: DokumentHendelse, sessionContext: SessionContext? = null)
    fun hentForSak(sakId: UUID, sessionContext: SessionContext): List<DokumentHendelse>
    fun hentHendelse(hendelseId: HendelseId, sessionContext: SessionContext? = null): DokumentHendelse?
    fun hentFilFor(hendelseId: HendelseId, sessionContext: SessionContext? = null): HendelseFil?
    fun hentHendelseOgFilFor(hendelseId: HendelseId, sessionContext: SessionContext? = null): Pair<DokumentHendelse?, HendelseFil?>
}
