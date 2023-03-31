package no.nav.su.se.bakover.web.routes.skatt

import arrow.core.Either
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.service.skatt.HentSamletSkattegrunnlagForBehandlingResponse

fun HentSamletSkattegrunnlagForBehandlingResponse.mapAndSerialize(): String =
    """
    {
        "skatteoppslagSøker": ${
    when (val x = this.skatteoppslagSøker) {
        is Either.Left -> serialize(x.value.tilResultat())
        is Either.Right -> serialize(x.value.toJSON())
    }
    },
        "skatteoppslagEps": ${
    when (val x = this.skatteoppslagEps) {
        is Either.Left -> serialize(x.value.tilResultat())
        is Either.Right -> serialize(x.value.toJSON())
        null -> null
    }
    }
    }
    """.trimIndent()
