package com.onesignal.core.internal.application

/**
 * Marker for SDK-internal, transient activities (such as the notification-open trampolines) that
 * must be excluded from app focus / foreground tracking.
 *
 * These activities are launched by the SDK itself, often before the host app's real activity
 * resumes, and they finish immediately after handling their intent. Counting them toward focus
 * would prematurely toggle [IApplicationService.isInForeground] / entry state and corrupt the
 * activity reference count once the SDK observes the full activity lifecycle from process start.
 */
interface OneSignalInternalActivity
