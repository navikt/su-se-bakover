package tilbakekreving.infrastructure.repo.avbrutt

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.dokument.infrastructure.BrevvalgDbJson
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.AvbruttHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

private data class AvbruttHendelseDbJsonHendelseDbJson(
    val behandlingsId: UUID,
    val utførtAv: String,
    val brevvalgDbJson: BrevvalgDbJson,
)

internal fun mapToTilAvbruttHendelse(
    data: String,
    hendelseId: HendelseId,
    sakId: UUID,
    hendelsestidspunkt: Tidspunkt,
    versjon: Hendelsesversjon,
    meta: DefaultHendelseMetadata,
    tidligereHendelseId: HendelseId,
): AvbruttHendelse {
    val deserialized = deserialize<AvbruttHendelseDbJsonHendelseDbJson>(data)

    return AvbruttHendelse(
        hendelseId = hendelseId,
        sakId = sakId,
        hendelsestidspunkt = hendelsestidspunkt,
        versjon = versjon,
        meta = meta,
        id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
        utførtAv = NavIdentBruker.Saksbehandler(deserialized.utførtAv),
        tidligereHendelseId = tidligereHendelseId,
        brevvalg = when (deserialized.brevvalgDbJson.type) {
            BrevvalgDbJson.Type.SAKSBEHANDLER_VALG_SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST -> Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst(
                deserialized.brevvalgDbJson.fritekst!!,
            )

            BrevvalgDbJson.Type.SAKSBEHANDLER_VALG_SKAL_IKKE_SENDE_BREV -> Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev(
                deserialized.brevvalgDbJson.begrunnelse!!,
            )

            else -> throw IllegalStateException("Ikke støtte for å lage andre typer brevvalg")
        },
        begrunnelse = deserialized.brevvalgDbJson.begrunnelse!!,
    )
}

internal fun AvbruttHendelse.toJson(): String {
    return AvbruttHendelseDbJsonHendelseDbJson(
        behandlingsId = this.id.value,
        utførtAv = this.utførtAv.navIdent,
        brevvalgDbJson = BrevvalgDbJson(
            fritekst = when (this.brevvalg) {
                is Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev -> null
                is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst -> this.brevvalg.fritekst
                is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev -> throw IllegalStateException("Skal ikke kunne sende vedtaksbrev i forbindelse med avbrutt tilbakekreving")
            },
            begrunnelse = this.begrunnelse,
            type = when (this.brevvalg) {
                is Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev -> BrevvalgDbJson.Type.SAKSBEHANDLER_VALG_SKAL_IKKE_SENDE_BREV
                is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst -> BrevvalgDbJson.Type.SAKSBEHANDLER_VALG_SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST
                is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev -> throw IllegalStateException("Skal ikke kunne sende vedtaksbrev i forbindelse med avbrutt tilbakekreving")
            },
        ),
    ).let { serialize(it) }
}
