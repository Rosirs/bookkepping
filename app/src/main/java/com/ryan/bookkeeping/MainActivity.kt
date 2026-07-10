package com.ryan.bookkeeping

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ryan.bookkeeping.notification.AppDependencies
import com.ryan.bookkeeping.ui.BookKeepingApp
import com.ryan.bookkeeping.ui.BookkeepingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = BookkeepingViewModel(AppDependencies.repository(applicationContext))
        setContent {
            BookKeepingApp(viewModel = viewModel)
        }
    }
}
