package com.onesignal.user.tags

import com.onesignal.user.collections.OSCollection

/**
 * A data tag is a key/value pair that describes the user.
 */
class TagCollection(tags: Collection<Tag>) : OSCollection<Tag>(tags) {}