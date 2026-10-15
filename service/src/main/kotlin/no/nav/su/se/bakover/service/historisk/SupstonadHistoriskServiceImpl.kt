package no.nav.su.se.bakover.service.historisk

import no.nav.su.se.bakover.client.historisk.SupstonadHistoriskClient
import no.nav.su.se.bakover.domain.historisk.HistoriskImportRepo
import no.nav.su.se.bakover.domain.historisk.HistoriskRådataLeser
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAlderProjeksjonRepo

class SupstonadHistoriskServiceImpl(
    supstonadHistoriskClient: SupstonadHistoriskClient,
    historiskImportRepo: HistoriskImportRepo,
    historiskRådataLeser: HistoriskRådataLeser? = null,
    historiskAlderProjeksjonRepo: HistoriskAlderProjeksjonRepo? = null,
) : SupstonadHistoriskService by SupstonadHistoriskServiceMedKonfigurasjon(
    supstonadHistoriskClient = supstonadHistoriskClient,
    historiskImportRepo = historiskImportRepo,
    historiskRådataLeser = historiskRådataLeser,
    historiskAlderProjeksjonRepo = historiskAlderProjeksjonRepo,
    konverteringskonfigurasjon = HistoriskKonverteringskonfigurasjon(),
)
