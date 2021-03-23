package no.nav.k9.ettersending

import no.nav.helse.dusseldorf.ktor.core.*
import no.nav.k9.vedlegg.Vedlegg
import java.net.URL

private const val MAX_VEDLEGG_SIZE = 24 * 1024 * 1024 // 3 vedlegg på 8 MB
private val vedleggTooLargeProblemDetails = DefaultProblemDetails(
    title = "attachments-too-large",
    status = 413,
    detail = "Totale størreslsen på alle vedlegg overstiger maks på 24 MB."
)

internal fun Ettersending.valider() {
    val violations: MutableSet<Violation> = mutableSetOf<Violation>()

    if(beskrivelse.isNullOrBlank()){
        violations.add(
            Violation(
                parameterName = "beskrivelse",
                parameterType = ParameterType.ENTITY,
                reason = "Beskrivelse kan ikke være tom eller blank",
                invalidValue = beskrivelse
            )
        )
    }

    if (!harBekreftetOpplysninger) {
        violations.add(
            Violation(
                parameterName = "harBekreftetOpplysninger",
                parameterType = ParameterType.ENTITY,
                reason = "Opplysningene må bekreftes for å sende inn ettersending.",
                invalidValue = false

            )
        )
    }

    if (!harForståttRettigheterOgPlikter) {
        violations.add(
            Violation(
                parameterName = "harForståttRettigheterOgPlikter",
                parameterType = ParameterType.ENTITY,
                reason = "Må ha forstått rettigheter og plikter for å sende inn ettersending.",
                invalidValue = false
            )
        )
    }

    //TODO 23.03.2021 - Burde det være en validering på at det er minst et vedlegg? Ettersending uten vedlegg er vel ikke noe vits?

    if (violations.isNotEmpty()) {
        throw Throwblem(ValidationProblemDetails(violations))
    }
}

internal fun List<Vedlegg>.validerVedlegg(vedleggUrler: List<URL>) {
    if (size != vedleggUrler.size) {
        throw Throwblem(
            ValidationProblemDetails(
                violations = setOf(
                    Violation(
                        parameterName = "vedlegg",
                        parameterType = ParameterType.ENTITY,
                        reason = "Mottok referanse til ${vedleggUrler.size} vedlegg, men fant kun $size vedlegg.",
                        invalidValue = vedleggUrler
                    )
                )
            )
        )
    }
    validerTotalStorresle()
}

private fun List<Vedlegg>.validerTotalStorresle() {
    val totalSize = sumBy { it.content.size }
    if (totalSize > MAX_VEDLEGG_SIZE) {
        throw Throwblem(vedleggTooLargeProblemDetails)
    }
}
