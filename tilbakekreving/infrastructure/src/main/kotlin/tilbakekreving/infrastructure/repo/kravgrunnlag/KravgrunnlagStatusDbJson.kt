package tilbakekreving.infrastructure.repo.kravgrunnlag

import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus

fun Kravgrunnlagstatus.toDbString(): String =
    when (this) {
        Kravgrunnlagstatus.Annullert -> "Annullert"
        Kravgrunnlagstatus.AnnullertVedOmg -> "AnnullertVedOmg"
        Kravgrunnlagstatus.Avsluttet -> "Avsluttet"
        Kravgrunnlagstatus.Ferdigbehandlet -> "Ferdigbehandlet"
        Kravgrunnlagstatus.Endret -> "Endret"
        Kravgrunnlagstatus.Feil -> "Feil"
        Kravgrunnlagstatus.Manuell -> "Manuell"
        Kravgrunnlagstatus.Nytt -> "Nytt"
        Kravgrunnlagstatus.Sperret -> "Sperret"
    }

fun String.toKravgrunnlagStatus(): Kravgrunnlagstatus {
    return when (this) {
        "Annullert" -> Kravgrunnlagstatus.Annullert
        "AnnullertVedOmg" -> Kravgrunnlagstatus.AnnullertVedOmg
        "Avsluttet" -> Kravgrunnlagstatus.Avsluttet
        "Ferdigbehandlet" -> Kravgrunnlagstatus.Ferdigbehandlet
        "Endret" -> Kravgrunnlagstatus.Endret
        "Feil" -> Kravgrunnlagstatus.Feil
        "Manuell" -> Kravgrunnlagstatus.Manuell
        "Nytt" -> Kravgrunnlagstatus.Nytt
        "Sperret" -> Kravgrunnlagstatus.Sperret
        else -> throw IllegalStateException("Ukjent persistert kravgrunnlagsstatus på KravgrunnlagPåSakHendelse: $this")
    }
}
