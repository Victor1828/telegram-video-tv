package com.example.telegramvideotv.ui.auth

import com.example.telegramvideotv.data.auth.AuthState

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rejowan.ccpc.CCPUtils
import com.rejowan.ccpc.Country
import com.rejowan.ccpc.CountryCodePickerTextField


@Composable
fun LoginScreen (viewModel: AuthViewModel) {
//    val state by viewModel.uiState.collectAsState()
//    var countryCode by remember { mutableStateOf("") }
//    var phoneNumber by remember { mutableStateOf("") }
//    var selectedCountry by remember { mutableStateOf(Country.Mexico) }
//    var inputValue by remember { mutableStateOf("") }
//
//    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
//        when (state) {
//            is AuthState.WaitPhoneNumber -> {
//                CountryCodePickerTextField(
//                    number = phoneNumber,
//                    onValueChange = { country, number, isValid ->
//                        //selectedCountry = Country.valueOf(country)
//                        countryCode = country
//                        phoneNumber = number
//                    },
//                    selectedCountry = selectedCountry,
//                    label = { Text("Phone Number") },
//                    showError = true  // Show validation errors
//                )
//
//                Spacer(modifier = Modifier.height(16.dp))
//
//                Button(
//                    onClick = { viewModel.onPhoneEntered("${countryCode}${phoneNumber}") },
//                    modifier = Modifier.fillMaxWidth(),
//                ) {
//                    Text("Enviar número")
//                }
//            }
//            is AuthState.WaitCode -> {
//                AuthInputField(
//                    label = "Código de verificación",
//                    value = inputValue,
//                    onValueChange = { inputValue = it },
//                    buttonText = "Verificar código",
//                    onButtonClick = { viewModel.onCodeEntered(inputValue) }
//                )
//            }
//            is AuthState.Authenticated -> {
//                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp))
//                Text("¡Sesión Iniciada con éxito!")
//            }
//            is AuthState.Error -> {
//                Text("Error: ${(state as AuthState.Error).message}", color = Color.Red)
//                Button(onClick = { /* Reintentar o resetear */ }) { Text("Reintentar") }
//            }
//            else -> CircularProgressIndicator()
//        }
//    }
    val state by viewModel.uiState.collectAsState()
    var countryCode by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var inputValue by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(Country.Mexico) }

//    // 1. Extraemos el contexto y el modo inspección en el nivel superior de la función
//    val context = LocalContext.current
//    // 2. Usamos LaunchedEffect para la detección automática al iniciar
//    LaunchedEffect(Unit) {
//        CCPUtils.getCountryAutomatically(context)?.let { country ->
//            Log.d("stream", "$country")
//            selectedCountry = country
//        }
//    }

    // Fondo oscuro característico de Telegram
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0e1621)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(450.dp), // Ancho fijo para que no se estire en pantallas 4K
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icono de Telegram
            Icon(
                imageVector = Icons.AutoMirrored.Default.Send, // Sustituye por el logo de Telegram
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFF2481cc)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Iniciar sesión en Telegram",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(40.dp))

            when (state) {
                is AuthState.WaitPhoneNumber -> {
                    PhoneInputSection(
                        phoneNumber = phoneNumber,
                        //onNumberChange = { phoneNumber = it },
                        onNumberChange = { country, number, isValid ->
                            countryCode = country
                            phoneNumber = number
                        },
                        //onSend = { viewModel.onPhoneEntered(phoneNumber) }
                        onSend = { viewModel.onPhoneEntered("${countryCode}${phoneNumber}") },
                        selectedCountry = selectedCountry
                    )
                }
                is AuthState.WaitCode -> {
                    CodeInputSection(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        onVerify = { viewModel.onCodeEntered(inputValue) }
                    )
                }
                is AuthState.Authenticated -> {
                    Text("¡Bienvenido!", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    // Aquí podrías disparar una navegación automática
                }
                is AuthState.Error -> {
                    Text("Error: ${(state as AuthState.Error).message}", color = Color(0xFFE57373))
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { /* Reset ViewModel state */ }) { Text("Intentar de nuevo") }
                }
                else -> CircularProgressIndicator(color = Color(0xFF2481cc))
            }
        }
    }
}

@Composable
fun PhoneInputSection(phoneNumber: String, onNumberChange: (String, String, Boolean) -> Unit, onSend: () -> Unit, selectedCountry: Country) {
    Column {
//        OutlinedTextField(
//            value = phoneNumber,
//            onValueChange = onNumberChange,
//            label = { Text("Número de teléfono (ej: +52...)") },
//            modifier = Modifier.fillMaxWidth(),
//            singleLine = true,
//            colors = OutlinedTextFieldDefaults.colors(
//                focusedTextColor = Color.White,
//                unfocusedTextColor = Color.White,
//                focusedBorderColor = Color(0xFF2481cc),
//                unfocusedBorderColor = Color.Gray,
//                focusedLabelColor = Color(0xFF2481cc)
//            ),
//            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
//        )
        CountryCodePickerTextField(
            number = phoneNumber,
            onValueChange = onNumberChange,
            selectedCountry = selectedCountry,
            label = { Text(text = "Número de teléfono (ej: +52...)", color = Color.White) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                errorTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF2481cc),
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color(0xFF2481cc),
                cursorColor = Color.White,
                focusedLeadingIconColor = Color.White,
                unfocusedLeadingIconColor = Color.White,
                errorLeadingIconColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            showError = true,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSend,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.colors(containerColor = Color(0xFF2481cc))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "CONTINUAR", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun CodeInputSection(value: String, onValueChange: (String) -> Unit, onVerify: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Hemos enviado un código a tu aplicación móvil",
            color = Color(0xFF708499),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(200.dp),
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                textAlign = TextAlign.Center,
                letterSpacing = 8.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                errorTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF2481cc),
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color(0xFF2481cc),
                cursorColor = Color.White,
                focusedLeadingIconColor = Color.White,
                unfocusedLeadingIconColor = Color.White,
                errorLeadingIconColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onVerify,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.colors(containerColor = Color(0xFF2481cc))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("VERIFICAR")
            }
        }
    }
}

@Composable
fun AuthInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onButtonClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(buttonText)
    }
}