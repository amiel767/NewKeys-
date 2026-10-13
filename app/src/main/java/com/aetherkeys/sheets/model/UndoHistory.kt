package com.aetherkeys.sheets.model

import java.util.concurrent.ConcurrentHashMap

class UndoHistory<T>(private val maxEntries: Int = 50) {
    private val past = mutableListOf<T>()
    private val future = mutableListOf<T>()
    private var lastGroup: String? = null
    private var lastRecordTime: Long = 0

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun record(state: T, group: String? = null, timeMs: Long = System.currentTimeMillis()) {
        future.clear()
        if (group != null && group == lastGroup && timeMs - lastRecordTime < 1000L) {
            // Coalesce edits in same group within 1s
            return
        }
        lastGroup = group
        lastRecordTime = timeMs
        past.add(state)
        if (past.size > maxEntries) {
            past.removeAt(0)
        }
    }

    fun undo(current: T): T? {
        if (past.isEmpty()) return null
        val previous = past.removeAt(past.size - 1)
        future.add(current)
        lastGroup = null
        return previous
    }

    fun redo(current: T): T? {
        if (future.isEmpty()) return null
        val next = future.removeAt(future.size - 1)
        past.add(current)
        lastGroup = null
        return next
    }
}

object SessionHistories {
    private val histories = ConcurrentHashMap<String, UndoHistory<Sheet>>()

    fun forSheet(id: String): UndoHistory<Sheet> {
        return histories.getOrPut(id) { UndoHistory() }
    }

    fun clear() {
        histories.clear()
    }
}
