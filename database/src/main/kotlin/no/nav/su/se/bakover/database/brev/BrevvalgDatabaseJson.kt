package no.nav.su.se.bakover.database.brev

import no.nav.su.se.bakover.domain.brev.Brevvalg

data class BrevvalgDatabaseJson(
    val fritekst: String?,
    val begrunnelse: String?,
    val type: Type,
) {
    companion object {
        fun Brevvalg.toJson() = BrevvalgDatabaseJson(
            fritekst = this.fritekst,
            begrunnelse = this.begrunnelse,
            type = when (this) {
                is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst -> Type.SAKSBEHANDLER_VALG_SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST
                is Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev -> Type.SAKSBEHANDLER_VALG_SKAL_IKKE_SENDE_BREV
                is Brevvalg.SkalSendeBrev.InformasjonsbrevMedFritekst -> Type.SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST
                is Brevvalg.SkalIkkeSendeBrev -> Type.SKAL_IKKE_SENDE_BREV
                is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.UtenFritekst -> Type.SAKSBEHANDLER_VALG_SKAL_SENDE_VEDTAKSBREV_UTEN_FRITEKST
                is Brevvalg.SkalSendeBrev.InformasjonsbrevUtenFritekst -> Type.SKAL_SENDE_INFORMASJONSBREV_UTEN_FRITEKST
                is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst -> Type.SAKSBEHANDLER_VALG_SKAL_SENDE_VEDTAKSBREV_MED_FRITEKST
            },
        )
    }

    fun toDomain(): Brevvalg {
        return when (this.type) {
            Type.SAKSBEHANDLER_VALG_SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST -> Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst(
                fritekst = this.fritekst!!,
            )
            Type.SAKSBEHANDLER_VALG_SKAL_IKKE_SENDE_BREV -> Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev(
                begrunnelse = this.begrunnelse,
            )
            Type.SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST -> Brevvalg.SkalSendeBrev.InformasjonsbrevMedFritekst(
                fritekst = this.fritekst!!,
                begrunnelse = this.begrunnelse!!,
            )
            Type.SKAL_IKKE_SENDE_BREV -> Brevvalg.SkalIkkeSendeBrev(begrunnelse = this.begrunnelse!!)
            Type.SAKSBEHANDLER_VALG_SKAL_SENDE_VEDTAKSBREV_UTEN_FRITEKST -> Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.UtenFritekst(
                begrunnelse = this.begrunnelse,
            )
            Type.SKAL_SENDE_INFORMASJONSBREV_UTEN_FRITEKST -> Brevvalg.SkalSendeBrev.InformasjonsbrevMedFritekst(
                fritekst = this.fritekst!!,
                begrunnelse = this.begrunnelse!!,
            )

            Type.SAKSBEHANDLER_VALG_SKAL_SENDE_VEDTAKSBREV_MED_FRITEKST -> Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(
                begrunnelse = this.begrunnelse,
                fritekst = this.fritekst!!,
            )
        }
    }

    enum class Type {
        SAKSBEHANDLER_VALG_SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST,
        SAKSBEHANDLER_VALG_SKAL_IKKE_SENDE_BREV,
        SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST,
        SKAL_IKKE_SENDE_BREV,
        SAKSBEHANDLER_VALG_SKAL_SENDE_VEDTAKSBREV_UTEN_FRITEKST,
        SAKSBEHANDLER_VALG_SKAL_SENDE_VEDTAKSBREV_MED_FRITEKST,
        SKAL_SENDE_INFORMASJONSBREV_UTEN_FRITEKST,
    }
}
