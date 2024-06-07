package dokument.domain.hendelser

import dokument.domain.Dokument
import dokument.domain.DokumentHendelser
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import java.time.LocalDate
import java.util.UUID

val GenerertDokument = Hendelsestype("GENERERT_DOKUMENT")
val JournalførtDokument = Hendelsestype("JOURNALTFØRT_DOKUMENT")
val DistribuertDokument = Hendelsestype("DISTRIBUERT_DOKUMENT")

interface DokumentHendelseRepo {
    fun lagreGenerertDokumentHendelse(
        hendelse: GenerertDokumentHendelse,
        hendelseFil: HendelseFil,
        meta: DefaultHendelseMetadata,
        sessionContext: SessionContext? = null,
    )

    fun lagreJournalførtDokumentHendelse(
        hendelse: JournalførtDokumentHendelse,
        meta: DefaultHendelseMetadata,
        sessionContext: SessionContext? = null,
    )

    fun lagreDistribuertDokumentHendelse(
        hendelse: DistribuertDokumentHendelse,
        meta: DefaultHendelseMetadata,
        sessionContext: SessionContext? = null,
    )

    fun hentDokumentHendelserForSakId(sakId: UUID, sessionContext: SessionContext? = null): DokumentHendelser

    fun hentDokumentMedMetadataForSakId(sakId: UUID, sessionContext: SessionContext? = null): List<Dokument.MedMetadata>

    fun hentDokumentMedMetadataForSakIdOgDokumentId(
        sakId: UUID,
        dokumentId: UUID,
        sessionContext: SessionContext? = null,
    ): Dokument.MedMetadata?
    fun hentHendelse(hendelseId: HendelseId, sessionContext: SessionContext? = null): DokumentHendelse?
    fun hentFilFor(hendelseId: HendelseId, sessionContext: SessionContext? = null): HendelseFil?
    fun hentHendelseOgFilFor(hendelseId: HendelseId, sessionContext: SessionContext? = null): Pair<DokumentHendelse?, HendelseFil?>
    fun hentHendelseOgFilForDokumentId(dokumentId: UUID, sessionContext: SessionContext? = null): Pair<DokumentHendelse?, HendelseFil?>

    fun hentHendelseForDokumentId(dokumentId: UUID, sessionContext: SessionContext? = null): DokumentHendelse?
    fun hentHendelseForRelatertHendelseId(relatertHendelseId: HendelseId, sessionContext: SessionContext? = null): DokumentHendelse?
    fun hentVedtaksbrevdatoForSakOgVedtakId(sakId: UUID, vedtakId: UUID, sessionContext: SessionContext? = null): LocalDate?
}
