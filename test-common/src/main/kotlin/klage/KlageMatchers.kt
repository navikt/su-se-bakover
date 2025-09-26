package no.nav.su.se.bakover.test.klage

import io.kotest.assertions.eq.actualIsNull
import io.kotest.assertions.eq.expectedIsNull
import io.kotest.matchers.equality.FieldsEqualityCheckConfig
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlageFelter
import no.nav.su.se.bakover.domain.klage.FerdigstiltOmgjortKlage
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlageFelter
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import kotlin.reflect.KProperty

fun Klagevedtak.Avvist.shouldBeEqualComparingPublicFieldsAndInterface(
    expected: Klagevedtak.Avvist,
    vararg others: KProperty<*>,
) {
    this.shouldBeEqualToIgnoringFields(other = expected, property = Klagevedtak.Avvist::behandling, others = others)
    this.behandling.shouldBeEqualComparingPublicFieldsAndInterface(expected.behandling)
}

fun List<Klage?>.shouldBeEqualComparingPublicFieldsAndInterface(expected: List<Klage?>) {
    this.zip(expected).forEach {
        it.first.shouldBeEqualComparingPublicFieldsAndInterface(it.second)
    }
}

fun Klage?.shouldBeEqualComparingPublicFieldsAndInterface(expected: Klage?, ignoreProperty: KProperty<*>? = null) {
    if (this == null) {
        if (expected == null) {
            return
        } else {
            actualIsNull(expected)
        }
    }
    if (expected == null) expectedIsNull(this!!)

    if (ignoreProperty != null) {
        this!!.shouldBeEqualToIgnoringFields(expected!!, true, ignoreProperty)
    } else {
        this!!.shouldBeEqualToComparingFields(other = expected!!, FieldsEqualityCheckConfig(ignorePrivateFields = true))
    }
    when (this) {
        is OpprettetKlage -> this.shouldBe(expected)
        is VilkårsvurdertKlage.Påbegynt -> this.shouldBe(expected)
        is VilkårsvurdertKlage.Utfylt.Avvist -> this.shouldBe(expected)
        is VilkårsvurdertKlage.Utfylt.TilVurdering -> this.shouldBe(expected)
        is VilkårsvurdertKlage.Bekreftet.Avvist -> this.shouldBe(expected)
        is VilkårsvurdertKlage.Bekreftet.TilVurdering -> this.shouldBe(expected)
        // Vi gjør en cast til delegate by interfacet før vi sammenligner resten av feltene
        is AvvistKlage -> this.castAndCompare<VilkårsvurdertKlage.BekreftetFelter>(expected, ignoreProperty)
        is VurdertKlage.Påbegynt -> this.castAndCompare<VilkårsvurdertKlage.Bekreftet.TilVurderingFelter>(
            expected,
            ignoreProperty,
        )

        is VurdertKlage.Utfylt -> this.castAndCompare<VurdertKlageFelter>(expected, ignoreProperty)
        is VurdertKlage.Bekreftet -> this.castAndCompare<VurdertKlage.UtfyltFelter>(expected, ignoreProperty)
        is KlageTilAttestering.Avvist -> this.castAndCompare<AvvistKlageFelter>(expected, ignoreProperty)
        is KlageTilAttestering.Vurdert -> this.castAndCompare<VurdertKlage.UtfyltFelter>(expected, ignoreProperty)
        is AvsluttetKlage -> {
            this.castAndCompare<Klage>(expected, ignoreProperty)
            // Gjør en spesialsjekk på den underliggende typen som kan variere.
            // Den brukes både ved instansiering fra databasen og serialisering til json.
            this.hentUnderliggendeKlage()
                .shouldBeEqualComparingPublicFieldsAndInterface(
                    (expected as AvsluttetKlage).hentUnderliggendeKlage(),
                    Klage::saksbehandler,
                )
        }

        is OversendtKlage -> this.castAndCompare<VurdertKlage.UtfyltFelter>(expected)
        is IverksattAvvistKlage -> this.castAndCompare<AvvistKlageFelter>(expected)
        is FerdigstiltOmgjortKlage -> this.castAndCompare<FerdigstiltOmgjortKlage>(expected)
    }
}

private fun <T> Klage.castAndCompare(expected: Klage, ignoreProperty: KProperty<*>? = null) {
    @Suppress("UNCHECKED_CAST")
    if (ignoreProperty != null) {
        (this as T)!!.shouldBeEqualToIgnoringFields((expected as T)!!, ignoreProperty)
    } else {
        (this as T)!!.shouldBeEqualToComparingFields((expected as T)!!)
    }
}
