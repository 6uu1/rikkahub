package me.rerere.rikkahub.di

import me.rerere.rikkahub.ui.pages.assistant.AssistantVM
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailVM
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.ui.pages.debug.DebugVM
import me.rerere.rikkahub.ui.pages.history.HistoryVM
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.pages.setting.WebDavViewModel // Added import for WebDavViewModel
import me.rerere.rikkahub.ui.pages.translator.TranslatorVM
import org.koin.android.ext.koin.androidApplication // Added import for androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel // Added import for viewModel DSL
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::ChatVM)
    viewModelOf(::SettingVM)
    viewModelOf(::DebugVM)
    viewModelOf(::HistoryVM)
    viewModelOf(::AssistantVM)
    viewModelOf(::AssistantDetailVM)
    viewModelOf(::TranslatorVM)
    viewModel { WebDavViewModel(androidApplication(), get()) } // Added WebDavViewModel
}