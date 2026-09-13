package com.fintrack.app.di

import com.fintrack.app.data.FlowStore
import com.fintrack.app.data.GoalStore
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.OtaUpdateRepository
import com.fintrack.app.data.repository.PatternRepository
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.ui.auth.AuthViewModel
import com.fintrack.app.ui.budget.BudgetViewModel
import com.fintrack.app.ui.calendar.CalendarViewModel
import com.fintrack.app.ui.dashboard.DashboardViewModel
import com.fintrack.app.ui.flows.FlowsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { AuthRepository() }
    single { TransactionRepository() }
    single { OtaUpdateRepository() }
    single { PatternRepository() }
    single { FlowStore(androidContext()) }
    single { GoalStore(androidContext()) }
    viewModel { DashboardViewModel(get(), get(), get()) }
    viewModel { AuthViewModel(get()) }
    viewModel { CalendarViewModel(get(), get(), get()) }
    viewModel { FlowsViewModel(get(), get(), get(), get()) }
    viewModel { BudgetViewModel(get(), get(), get(), get()) }
}
