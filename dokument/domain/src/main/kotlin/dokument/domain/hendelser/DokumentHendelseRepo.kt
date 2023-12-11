package dokument.domain.hendelser

import dokument.domain.DokumentHendelser
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import java.util.UUID

val GenerertDokument = Hendelsestype("GENERERT_DOKUMENT")
val JournalførtDokument = Hendelsestype("JOURNALTFØRT_DOKUMENT")
val DistribuertDokument = Hendelsestype("DISTRIBUERT_DOKUMENT")

interface DokumentHendelseRepo {
    fun lagre(
        hendelse: DokumentHendelse,
        meta: DefaultHendelseMetadata,
        hendelseFil: HendelseFil,
        sessionContext: SessionContext? = null,
    )

    fun lagre(hendelse: DokumentHendelse, meta: DefaultHendelseMetadata, sessionContext: SessionContext? = null)
    fun hentForSak(sakId: UUID, sessionContext: SessionContext? = null): DokumentHendelser
    fun hentHendelse(hendelseId: HendelseId, sessionContext: SessionContext? = null): DokumentHendelse?
    fun hentFilFor(hendelseId: HendelseId, sessionContext: SessionContext? = null): HendelseFil?
    fun hentHendelseOgFilFor(hendelseId: HendelseId, sessionContext: SessionContext? = null): Pair<DokumentHendelse?, HendelseFil?>
    fun hentHendelseOgFilForDokument(dokumentId: UUID, sessionContext: SessionContext? = null): Pair<DokumentHendelse?, HendelseFil?>
    fun hentDokumentHendelseForRelatert(relatertHendelseId: HendelseId, sessionContext: SessionContext? = null): DokumentHendelse?
}
