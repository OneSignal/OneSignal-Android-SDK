package com.onesignal.session

/**
 * The OneSignal session manager is responsible for managing the current session state.
 */
interface ISessionManager {
    /**
     * Add an outcome with the provided name, captured against the current session.
     * See [Outcomes | OneSignal] (https://documentation.onesignal.com/docs/outcomes)
     *
     * @param name The name of the outcome that has occurred.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun addOutcome(name: String): ISessionManager

    /**
     * Add a unique outcome with the provided name, captured against the current session.
     * See [Outcomes | OneSignal] (https://documentation.onesignal.com/docs/outcomes)
     *
     * @param name The name of the unique outcome that has occurred.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun addUniqueOutcome(name: String): ISessionManager

    /**
     * Add an outcome with the provided name and value, captured against the current session.
     * See [Outcomes | OneSignal] (https://documentation.onesignal.com/docs/outcomes)
     *
     * @param name The name of the outcome that has occurred.
     * @param value The value tied to the outcome.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun addOutcomeWithValue(name: String, value: Float): ISessionManager
}
