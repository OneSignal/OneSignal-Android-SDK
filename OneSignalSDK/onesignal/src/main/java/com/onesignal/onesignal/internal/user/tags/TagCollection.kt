package com.onesignal.onesignal.internal.user.tags

import com.onesignal.onesignal.internal.collections.OSCollection

/**
 * A data tag is a key/value pair that describes the user.
 */
class TagCollection(tags: Collection<Tag>) : OSCollection<Tag>(tags) {}