package no.nav.su.se.bakover.web.routes.drift

import no.nav.su.se.bakover.service.søknadsbehandling.FerdigstillIverksettingService

data class FixIverksettingerResponseJson(
    val journalposteringer: Journalposteringer,
    val brevbestillinger: Brevbestillinger,
) {
    data class Journalposteringer(
        val ok: List<Journalpost>,
        val feilet: List<Feilet>
    )

    data class Journalpost(
        val sakId: String,
        val behandlingId: String,
        val journalpostId: String,
    )

    data class Brevbestillinger(
        val ok: List<Brevbestilling>,
        val feilet: List<Feilet>
    )

    data class Brevbestilling(
        val sakId: String,
        val behandlingId: String,
        val brevbestillingId: String,
    )

    data class Feilet(
        val sakId: String,
        val behandlingId: String,
        val grunn: String
    )

    companion object {
        fun FerdigstillIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat.toJson() = FixIverksettingerResponseJson(
            journalposteringer = Journalposteringer(
                ok = this.journalpostresultat.mapNotNull { it.orNull() }.map {
                    Journalpost(
                        sakId = it.sakId.toString(),
                        behandlingId = it.behandlingId.toString(),
                        journalpostId = it.journalpostId.toString()
                    )
                },
                feilet = this.journalpostresultat.mapNotNull { it.swap().orNull() }.map {
                    Feilet(
                        sakId = it.sakId.toString(),
                        behandlingId = it.behandlingId.toString(),
                        grunn = it.grunn
                    )
                }
            ),
            brevbestillinger = Brevbestillinger(
                ok = this.brevbestillingsresultat.mapNotNull { it.orNull() }.map {
                    Brevbestilling(
                        sakId = it.sakId.toString(),
                        behandlingId = it.behandlingId.toString(),
                        brevbestillingId = it.brevbestillingId.toString()
                    )
                },
                feilet = this.brevbestillingsresultat.mapNotNull { it.swap().orNull() }.map {
                    Feilet(
                        sakId = it.sakId.toString(),
                        behandlingId = it.behandlingId.toString(),
                        grunn = it.grunn
                    )
                }
            )
        )
    }
}
