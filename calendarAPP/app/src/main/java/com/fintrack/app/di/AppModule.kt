package com.fintrack.app.di

import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.OtaUpdateRepository
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.ui.auth.AuthViewModel
import com.fintrack.app.ui.dashboard.DashboardViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { AuthRepository() }
    single { TransactionRepository() }
    single { OtaUpdateRepository() }
    viewModel { DashboardViewModel(get(), get(), get()) }
    viewModel { AuthViewModel(get()) }
}
