package com.onesignal.core.internal.operations

import com.onesignal.common.modeling.Model

/**
 * An [Operation] can be enqueued and executed on the [IOperationRepo]. Each concrete-class
 * of an [Operation] has a unique [name] to identity the type of operation, and contains
 * additional properties required to execute that specific operation.  Execution of an operation is
 * performed by an [IOperationExecutor]. [IOperationExecutor] identifies itself as being
 * able to execute an [Operation] through [IOperationExecutor.operations].
 */
abstract class Operation(name: String) : Model() {
    var name: String
        get() = getProperty(::name.name)
        private set(value) { setProperty(::name.name, value) }

    init {
        this.name = name
    }

    /**
     * The key of this operation for when the starting operation has a [groupComparisonType]
     * of [GroupComparisonType.CREATE]
     */
    abstract val createComparisonKey: String

    /**
     * The key of this operation for when the starting operation has a [groupComparisonType]
     * of [GroupComparisonType.ALTER]
     */
    abstract val modifyComparisonKey: String

    /**
     * The comparison type to use when this operation is the starting operation, in terms of
     * which operations can be grouped with it.
     */
    abstract val groupComparisonType: GroupComparisonType

    /**
     * Whether the operation can currently execute given it's current state.
     */
    abstract val canStartExecute: Boolean

    override fun toString(): String {
        return toJSON().toString()
    }
}

enum class GroupComparisonType {
    CREATE,
    ALTER,
    NONE
}
