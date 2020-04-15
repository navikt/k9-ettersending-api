package no.nav.k9.general

import io.ktor.application.ApplicationCall
import io.ktor.features.callId

data class CallId(val value : String)

fun ApplicationCall.getCallId() : CallId {
    return CallId(callId!!)
}
