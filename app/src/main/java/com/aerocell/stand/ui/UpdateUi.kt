package com.aerocell.stand.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aerocell.stand.BuildConfig
import com.aerocell.stand.StandUiState
import com.aerocell.stand.ui.theme.AeroMuted
import com.aerocell.stand.ui.theme.AeroPanel
import com.aerocell.stand.ui.theme.AeroRed
import com.aerocell.stand.ui.theme.AeroSilver

@Composable
fun UpdateBar(
    state: StandUiState,
    onStartUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showBanner = state.updateAvailable
    val status = state.contentStatusText
    if (!showBanner && status.isNullOrBlank()) return
    Column(modifier = modifier.fillMaxWidth()) {
        if (showBanner) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AeroPanel)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val version = state.updateVersionName.orEmpty()
                val title = if (state.updateRequired) {
                    "Нужно обновить приложение" + if (version.isNotBlank()) " $version" else ""
                } else {
                    "Доступно обновление" + if (version.isNotBlank()) " $version" else ""
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                val buttonLabel = when {
                    state.apkBusy && state.apkProgress != null ->
                        "${(state.apkProgress * 100).toInt()}%"
                    state.apkBusy -> "…"
                    else -> "Обновить"
                }
                Text(
                    text = buttonLabel,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AeroRed)
                        .clickable(enabled = !state.apkBusy, onClick = onStartUpdate)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        if (!status.isNullOrBlank() && !(showBanner && state.apkBusy)) {
            Text(
                text = status,
                color = AeroMuted,
                fontSize = 11.sp,
                letterSpacing = 0.4.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (showBanner) 6.dp else 0.dp)
            )
        }
    }
}

@Composable
fun ServiceMenuDialog(
    state: StandUiState,
    onDismiss: () -> Unit,
    onShowIdle: () -> Unit,
    onCheckUpdates: () -> Unit,
    onStartUpdate: () -> Unit,
    onExitKiosk: (String) -> Boolean = { false },
    onResumeKiosk: () -> Unit = {},
    onClearKiosk: (String) -> Boolean = { false }
) {
    var pinAction by remember { mutableStateOf<String?>(null) }
    var pinValue by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AeroPanel)
                .padding(24.dp)
        ) {
            Text(
                text = "СЛУЖЕБНОЕ МЕНЮ",
                color = AeroMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(16.dp))
            MenuLine("Приложение", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            MenuLine("Пакет", BuildConfig.APPLICATION_ID)
            val content = state.contentVersion?.let { "версия $it, файлов в кэше ${state.cachedFiles}" }
                ?: "пока нет (встроенные треки)"
            MenuLine("Контент", content)
            MenuLine("Статус", state.contentStatusText ?: "—")
            MenuLine(
                "Режим простоя",
                "роликов в кэше: ${state.idleVideos.size}, включается через ${state.idleTimeoutSec} с, " +
                    "громкость роликов ${(state.idleVideoVolume * 100).toInt()} %"
            )
            val kioskStatus = when {
                !state.kioskEnabled -> "выключен"
                !state.kioskLockTaskDesired -> "на паузе"
                else -> "включён"
            }
            MenuLine("Киоск", kioskStatus)
            if (!state.lastError.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text = state.lastError, color = AeroRed, fontSize = 13.sp)
            }
            if (pinAction != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (pinAction == "clear") "PIN, чтобы снять режим киоска" else "PIN, чтобы выйти из киоска",
                    color = AeroSilver,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = pinValue,
                    onValueChange = {
                        pinValue = it
                        pinError = false
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (pinError) {
                    Spacer(Modifier.height(6.dp))
                    Text(text = "Неверный PIN", color = AeroRed, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuButton(
                        label = "ОК",
                        filled = true,
                        enabled = pinValue.isNotBlank(),
                        onClick = {
                            val ok = if (pinAction == "clear") {
                                onClearKiosk(pinValue)
                            } else {
                                onExitKiosk(pinValue)
                            }
                            if (ok) {
                                pinAction = null
                                pinValue = ""
                                pinError = false
                            } else {
                                pinError = true
                            }
                        }
                    )
                    MenuButton(
                        label = "Отмена",
                        filled = false,
                        enabled = true,
                        onClick = {
                            pinAction = null
                            pinValue = ""
                            pinError = false
                        }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            MenuButton(label = "Показать режим простоя", filled = false, enabled = true, onClick = onShowIdle)
            if (state.kioskEnabled && pinAction == null) {
                Spacer(Modifier.height(10.dp))
                if (state.kioskLockTaskDesired) {
                    MenuButton(
                        label = "Выйти из киоска",
                        filled = false,
                        enabled = true,
                        onClick = {
                            pinValue = ""
                            pinError = false
                            pinAction = "exit"
                        }
                    )
                } else {
                    MenuButton(
                        label = "Вернуть киоск",
                        filled = true,
                        enabled = true,
                        onClick = onResumeKiosk
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuButton(
                    label = if (state.checkingUpdates) "Проверяю…" else "Проверить обновления",
                    filled = true,
                    enabled = !state.checkingUpdates && !state.apkBusy,
                    onClick = onCheckUpdates
                )
                if (state.updateAvailable) {
                    MenuButton(
                        label = "Обновить",
                        filled = true,
                        enabled = !state.apkBusy,
                        onClick = onStartUpdate
                    )
                }
                MenuButton(label = "Закрыть", filled = false, enabled = true, onClick = onDismiss)
            }
            if (state.kioskEnabled && pinAction == null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Снять режим киоска",
                    color = AeroMuted,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable {
                            pinValue = ""
                            pinError = false
                            pinAction = "clear"
                        }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MenuLine(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(text = label, color = AeroMuted, fontSize = 11.sp, letterSpacing = 1.sp)
        Text(text = value, color = AeroSilver, fontSize = 15.sp)
    }
}

@Composable
private fun MenuButton(label: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (filled) AeroRed else Color.White.copy(alpha = 0.08f)
    Text(
        text = label,
        color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
