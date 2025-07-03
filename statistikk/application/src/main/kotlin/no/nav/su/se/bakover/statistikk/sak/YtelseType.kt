package no.nav.su.se.bakover.statistikk.sak

import no.nav.su.se.bakover.common.domain.sak.Sakstype

enum class YtelseType {
    SUUFORE,
    SUALDER,
}

fun Sakstype.toYtelseType() = when (this) {
    Sakstype.ALDER -> YtelseType.SUALDER
    Sakstype.UFØRE -> YtelseType.SUUFORE
}
