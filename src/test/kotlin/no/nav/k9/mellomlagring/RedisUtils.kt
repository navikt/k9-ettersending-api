package no.nav.k9.mellomlagring

import com.github.fppt.jedismock.RedisServer

internal fun RedisServer.started() = apply { start() }