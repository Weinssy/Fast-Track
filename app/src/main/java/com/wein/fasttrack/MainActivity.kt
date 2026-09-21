package com.wein.fasttrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.wein.fasttrack.data.AppDatabase
import com.wein.fasttrack.repository.ExpenseRepository
import com.wein.fasttrack.ui.FastTrackScreen
import com.wein.fasttrack.ui.theme.FastTrackTheme
import com.wein.fasttrack.viewmodel.ExpenseViewModel
import com.wein.fasttrack.viewmodel.ExpenseViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ExpenseRepository(database.expenseDao())
        val factory = ExpenseViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, factory)[ExpenseViewModel::class.java]

        setContent {
            FastTrackTheme {
                FastTrackScreen(viewModel = viewModel)
            }
        }
    }
}
