package com.example.telegramvideotv.data.auth

sealed class AuthState {
    object Idle : AuthState()
    object WaitPhoneNumber : AuthState()
    object WaitCode : AuthState()
    object WaitPassword : AuthState() // Para verificación en dos pasos
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
