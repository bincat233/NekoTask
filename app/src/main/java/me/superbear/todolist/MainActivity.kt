package me.superbear.todolist

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import me.superbear.todolist.ui.main.MainScreen
import me.superbear.todolist.ui.theme.TodolistTheme

class MainActivity : AppCompatActivity() {
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
