package no.nav.su.se.bakover.database.revurdering

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.brev.Brevvalg

data class AvsluttetRevurderingInfo(
    val begrunnelse: String,
    val brevvalg: BrevvalgJson?,
    val tidspunktAvsluttet: Tidspunkt,
) {
    data class BrevvalgJson(
        val fritekst: String?,
        val begrunnelse: String?,
        val type: Type,
    ) {
        companion object {
            fun Brevvalg.toJson() = BrevvalgJson(
                fritekst = this.fritekst,
                begrunnelse = this.begrunnelse,
                type = when (this) {
                    is Brevvalg.SaksbehandlersValg.SkalSendeBrev.MedFritekst -> Type.SAKSBEHANDLER_VALG_SKAL_SENDE_BREV_MED_FRITEKST
                    is Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev -> Type.SAKSBEHANDLER_VALG_SKAL_IKKE_SENDE_BREV
                    is Brevvalg.SkalSendeBrev.MedFritekst -> Type.SKAL_SENDE_BREV_MED_FRITEKST
                    is Brevvalg.SkalIkkeSendeBrev -> Type.SKAL_IKKE_SENDE_BREV
                },
            )
        }

        fun toDomain(): Brevvalg {
            return when (this.type) {
                Type.SAKSBEHANDLER_VALG_SKAL_SENDE_BREV_MED_FRITEKST -> Brevvalg.SaksbehandlersValg.SkalSendeBrev.MedFritekst(
                    fritekst = this.fritekst!!,
                )
                Type.SAKSBEHANDLER_VALG_SKAL_IKKE_SENDE_BREV -> Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev(
                    begrunnelse = this.begrunnelse!!,
                )
                Type.SKAL_SENDE_BREV_MED_FRITEKST -> Brevvalg.SkalSendeBrev.MedFritekst(
                    fritekst = this.fritekst!!,
                    begrunnelse = this.begrunnelse!!,
                )
                Type.SKAL_IKKE_SENDE_BREV -> Brevvalg.SkalIkkeSendeBrev(begrunnelse = this.begrunnelse!!)
            }
        }

        enum class Type {
            SAKSBEHANDLER_VALG_SKAL_SENDE_BREV_MED_FRITEKST,
            SAKSBEHANDLER_VALG_SKAL_IKKE_SENDE_BREV,
            SKAL_SENDE_BREV_MED_FRITEKST,
            SKAL_IKKE_SENDE_BREV,
        }
    }
}
