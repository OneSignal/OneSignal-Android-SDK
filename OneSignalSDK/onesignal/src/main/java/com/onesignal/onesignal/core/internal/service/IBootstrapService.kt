package com.onesignal.onesignal.core.internal.service

/**
 * A service that is expected to be part of the bootstrap process.  This isn't relevant to the service
 * infrastructure, but is a common interface used by services to indicate they want to be called as part of the
 * initialization process.
 */
interface IBootstrapService {
    fun bootstrap()
}