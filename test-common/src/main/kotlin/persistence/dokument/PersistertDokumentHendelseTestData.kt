package no.nav.su.se.bakover.test.persistence.dokument

import dokument.domain.Dokument
import dokument.domain.DokumentMedMetadataUtenFil
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.dokumentUtenFil
import no.nav.su.se.bakover.test.hendelse.defaultHendelseMetadata
import no.nav.su.se.bakover.test.hendelse.hendelseFil
import no.nav.su.se.bakover.test.hendelse.lagretDokumentForUtsendelseHendelse
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import java.util.UUID

class PersistertDokumentHendelseTestData(
    private val hendelseRepo: HendelseRepo,
    private val testDataHelper: TestDataHelper,
) {
    fun persisterDokumentHendelse(
        hendelseId: HendelseId = HendelseId.generer(),
        hendelsesTidspunkt: Tidspunkt = Tidspunkt.now(testDataHelper.clock),
        sakId: UUID,
        versjon: Hendelsesversjon = hendelseRepo.hentSisteVersjonFraEntitetId(sakId)!!.inc(),
        relaterteHendelse: HendelseId,
        generertDokumentJson: String = "{}",
        dokumentMetdata: Dokument.Metadata = Dokument.Metadata(
            sakId = sakId,
        ),
        dokumentUtenFil: DokumentMedMetadataUtenFil = dokumentUtenFil(
            generertDokumentJson = generertDokumentJson,
            metadata = dokumentMetdata,
        ),
        skalSendeBrev: Boolean = true,
    ) {
        val hendelse = lagretDokumentForUtsendelseHendelse(
            hendelseId = hendelseId,
            hendelsesTidspunkt = hendelsesTidspunkt,
            versjon = versjon,
            sakId = sakId,
            relaterteHendelse = relaterteHendelse,
            dokumentUtenFil = dokumentUtenFil,
            skalSendeBrev = skalSendeBrev,
        )
        testDataHelper.dokumentHendelseRepo.lagreGenerertDokumentHendelse(
            hendelse = hendelse,
            hendelseFil = hendelseFil(
                hendelseId = hendelseId,
            ),
            meta = defaultHendelseMetadata(),
        )
    }
}
