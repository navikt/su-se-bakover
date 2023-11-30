package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOf
import java.util.UUID

interface Grunnlag {
    val id: UUID

    val periode: Periode

    fun tilstøter(other: Grunnlag) = this.periode.tilstøter(other.periode)

    /** unnlater å sjekke på ID og opprettet */
    fun erLik(other: Grunnlag): Boolean

    fun tilstøterOgErLik(other: Grunnlag) = this.tilstøter(other) && this.erLik(other)
}

/**
 * Listen med periode trenger ikke være sammenhengende eller sortert.
 * Den kan og inneholde duplikater.
 */
fun NonEmptyList<Grunnlag>.periode(): Periode = this.map { it.periode }.minAndMaxOf()
