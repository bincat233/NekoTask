package me.superbear.todolist.ui.main.sections

import android.util.Log

internal fun <T> Result<T>.logFailure(tag: String, message: String): Result<T> {
    onFailure { error -> Log.e(tag, message, error) }
    return this
}
