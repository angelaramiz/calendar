package com.calendarfinance.app.di

import com.calendarfinance.app.data.repository.AuthRepository
import com.calendarfinance.app.data.repository.MovementRepository
import com.calendarfinance.app.data.repository.OtaUpdateRepository
import com.calendarfinance.app.data.repository.PatternRepository
import com.calendarfinance.app.ui.auth.AuthViewModel
import com.calendarfinance.app.ui.balance.BalanceViewModel
import com.calendarfinance.app.ui.calendar.CalendarViewModel
import com.calendarfinance.app.ui.movement.MovementViewModel
import com.calendarfinance.app.ui.ota.OtaUpdateViewModel
import com.calendarfinance.app.ui.pattern.PatternViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { AuthRepository() }
    single { MovementRepository() }
    single { PatternRepository() }
    single { OtaUpdateRepository() }

    viewModel { AuthViewModel(get()) }
    viewModel { CalendarViewModel(get(), get()) }
    viewModel { MovementViewModel(get(), get()) }
    viewModel { PatternViewModel(get()) }
    viewModel { BalanceViewModel(get()) }
    viewModel { OtaUpdateViewModel(get()) }
}
