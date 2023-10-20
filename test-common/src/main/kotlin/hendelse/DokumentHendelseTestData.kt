package no.nav.su.se.bakover.test.hendelse

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import dokument.domain.DokumentMedMetadataUtenFil
import dokument.domain.hendelser.LagretDokumentForJournalføringHendelse
import dokument.domain.hendelser.LagretDokumentForUtsendelseHendelse
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
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
    metadata: DefaultHendelseMetadata = DefaultHendelseMetadata.tom(),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    relaterteHendelser: Nel<HendelseId> = nonEmptyListOf(HendelseId.generer()),
    dokumentUtenFil: DokumentMedMetadataUtenFil = dokumentUtenFil(),
): LagretDokumentForJournalføringHendelse = LagretDokumentForJournalføringHendelse(
    hendelseId = hendelseId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    meta = metadata,
    sakId = sakId,
    relaterteHendelser = relaterteHendelser,
    dokumentUtenFil = dokumentUtenFil,
)

fun lagretDokumentForUtsendelseHendelse(
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsesTidspunkt: Tidspunkt = fixedTidspunkt,
    versjon: Hendelsesversjon = Hendelsesversjon(15),
    metadata: DefaultHendelseMetadata = DefaultHendelseMetadata.tom(),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    relaterteHendelser: Nel<HendelseId> = nonEmptyListOf(HendelseId.generer()),
    dokumentUtenFil: DokumentMedMetadataUtenFil = dokumentUtenFil(),
): LagretDokumentForUtsendelseHendelse = LagretDokumentForUtsendelseHendelse(
    hendelseId = hendelseId,
    hendelsestidspunkt = hendelsesTidspunkt,
    versjon = versjon,
    meta = metadata,
    sakId = sakId,
    relaterteHendelser = relaterteHendelser,
    dokumentUtenFil = dokumentUtenFil,
)

fun hendelseFil(
    hendelseId: HendelseId = HendelseId.generer(),
    fil: PdfA = PdfA("content".toByteArray()),
): HendelseFil = HendelseFil(
    hendelseId = hendelseId,
    fil = fil,
)
