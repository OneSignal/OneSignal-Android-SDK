package com.onesignal.core.internal.operations.results

interface IOperationResult {

}

enum class ReasonFailed {
    /**
     * The input was invalid.
     */
    INVALID_INPUT,

    /**
     * The login was missing but required.
     */
    LOGIN_REQUIRED,

    /**
     * The JWT was invalid.
     */
    INVALID_JWT,

    /**
     * The specific operation was denied.
     */
    OPERATION_SPECIFIC,
}

enum class ReasonRetrying {
    /**
     * No Connection.
     */
    NO_CONNECTION,

    /**
     * The server failed.
     */
    SERVER_FAILURE,

    /**
     * The JWT was invalid but should re-try
     */
    INVALID_JWT,
}