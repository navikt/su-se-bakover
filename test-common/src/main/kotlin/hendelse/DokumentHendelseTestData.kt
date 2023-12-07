package no.nav.su.se.bakover.test.hendelse

import dokument.domain.DokumentMedMetadataUtenFil
import dokument.domain.hendelser.GenerertDokumentHendelse
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.dokumentUtenFil
import no.nav.su.se.bakover.test.fixedTidspunkt
import java.util.UUID

fun lagretDokumentForJournalføringHendelse(
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = Hendelsesversjon(15),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    relaterteHendelse: HendelseId = HendelseId.generer(),
    dokumentUtenFil: DokumentMedMetadataUtenFil = dokumentUtenFil(),
    skalSendeBrev: Boolean = true,
): GenerertDokumentHendelse = GenerertDokumentHendelse(
    hendelseId = hendelseId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    sakId = sakId,
    relatertHendelse = relaterteHendelse,
    dokumentUtenFil = dokumentUtenFil,
    skalSendeBrev = skalSendeBrev,
)

fun lagretDokumentForUtsendelseHendelse(
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = Hendelsesversjon(15),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    relaterteHendelse: HendelseId = HendelseId.generer(),
    dokumentUtenFil: DokumentMedMetadataUtenFil = dokumentUtenFil(),
    skalSendeBrev: Boolean = true,
): GenerertDokumentHendelse = GenerertDokumentHendelse(
    hendelseId = hendelseId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    sakId = sakId,
    relatertHendelse = relaterteHendelse,
    dokumentUtenFil = dokumentUtenFil,
    skalSendeBrev = skalSendeBrev,
)

fun hendelseFil(
    hendelseId: HendelseId = HendelseId.generer(),
    fil: PdfA = PdfA("content".toByteArray()),
): HendelseFil = HendelseFil(
    hendelseId = hendelseId,
    fil = fil,
)
