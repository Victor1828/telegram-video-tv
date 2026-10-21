package com.example.telegramvideotv.ui.auth

import androidx.lifecycle.ViewModel
import com.example.telegramvideotv.data.auth.AuthRepository

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    val uiState = repository.authState

    fun onPhoneEntered(phone: String) {
        repository.sendPhoneNumber(phone)
    }

    fun onCodeEntered(code: String) {
        repository.sendCode(code)
    }
}
