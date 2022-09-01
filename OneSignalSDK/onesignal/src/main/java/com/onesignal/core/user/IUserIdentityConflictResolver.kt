package com.onesignal.core.user

interface IUserIdentityConflictResolver {
    /**
     * Given a conflict between the local user and the remote user,
     * construct a new user that represents a resolution to the
     * conflict.
     */
    fun resolve(local: IUserManager, remote: IUserManager): IUserManager
}
