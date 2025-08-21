package me.superbear.todolist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import me.superbear.todolist.ui.main.MainScreen
import me.superbear.todolist.ui.theme.TodolistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            TodolistTheme {
                MainScreen()
            }
        }
    }
}
