package com.aerocell.stand.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aerocell.stand.BuildConfig
import com.aerocell.stand.StandUiState
import com.aerocell.stand.WifiUiState
import com.aerocell.stand.update.UpdateButton
import com.aerocell.stand.ui.theme.AeroMuted
import com.aerocell.stand.ui.theme.AeroPanel
import com.aerocell.stand.ui.theme.AeroRed
import com.aerocell.stand.ui.theme.AeroSilver
import com.aerocell.stand.wifi.WifiNetwork
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Короткая строка статуса контента под ползунком. Кнопка обновления теперь в плашке сверху. */
@Composable
fun UpdateBar(
    state: StandUiState,
    modifier: Modifier = Modifier
) {
    val status = state.contentStatusText
    if (status.isNullOrBlank()) return
    Text(
        text = status,
        color = AeroMuted,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Плашка «Вышла версия X · Обновить» у верхнего края, левее места под кнопку «?».
 * Видна, когда обновление скачано и проверено, а тихо его поставить нельзя или стенд долго занят.
 */
@Composable
fun UpdatePill(
    state: StandUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = (state.updateButton != UpdateButton.NONE || state.updateButtonBusy) && state.updateScreen == null
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { -it / 3 },
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        val version = state.readyVersionName ?: state.updateVersionName.orEmpty()
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(AeroPanel)
                .border(1.dp, AeroRed.copy(alpha = 0.55f), RoundedCornerShape(32.dp))
                .clickable(enabled = !state.updateButtonBusy, onClick = onClick)
                .padding(start = 22.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AeroRed)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (state.updateButtonBusy) "Устанавливаем…" else "Вышла версия $version",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (!state.updateButtonBusy) {
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "Обновить",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(AeroRed)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale("ru"))
private fun fmtTime(ms: Long?): String = ms?.let { timeFormat.format(Date(it)) } ?: "—"

@Composable
fun ServiceMenuDialog(
    state: StandUiState,
    onDismiss: () -> Unit,
    onShowIdle: () -> Unit,
    onCheckUpdates: () -> Unit,
    onStartUpdate: () -> Unit,
    onOpenWifi: () -> Unit = {},
    onExitKiosk: () -> Unit = {},
    onResumeKiosk: () -> Unit = {},
    onClearKiosk: () -> Unit = {},
    onVizIntensity: (Int) -> Unit = {}
) {
    var confirmClear by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .widthIn(min = 400.dp, max = 560.dp)
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AeroPanel)
                .verticalScroll(rememberScrollState())
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
            val cloud = state.cloudVersionName?.let { "$it (${state.cloudVersionCode})" } ?: "нет данных"
            MenuLine("Версия в облаке", cloud)
            val lastOk = when (state.lastCheckOk) {
                true -> ""
                false -> ", не удалась"
                null -> ""
            }
            MenuLine(
                "Проверка облака",
                "последняя ${fmtTime(state.lastCheckAtMs)}$lastOk, следующая ${fmtTime(state.nextCheckAtMs)}"
            )
            MenuLine("Обновление", state.updateStatus ?: "—")
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
            MenuLine("Wi-Fi", state.wifi.currentSsid ?: "—")
            if (!state.lastError.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text = state.lastError, color = AeroRed, fontSize = 13.sp)
            }

            if (confirmClear) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Вы уверены? Тихие обновления и блокировка выхода пропадут, пока планшет снова не настроят скриптом.",
                    color = AeroSilver,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuButton(label = "Да, снять", filled = true, enabled = true, onClick = {
                        confirmClear = false
                        onClearKiosk()
                    })
                    MenuButton(label = "Отмена", filled = false, enabled = true, onClick = { confirmClear = false })
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuButton(label = "Показать режим простоя", filled = false, enabled = true, onClick = onShowIdle)
                MenuButton(label = "Wi-Fi", filled = false, enabled = true, onClick = onOpenWifi)
            }
            Spacer(Modifier.height(14.dp))
            Text("Визуализатор", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (p in listOf(0, 50, 100)) {
                    MenuButton(
                        label = "$p %",
                        filled = state.vizIntensity == p,
                        enabled = true,
                        onClick = { onVizIntensity(p) }
                    )
                }
            }
            if (state.kioskEnabled) {
                Spacer(Modifier.height(10.dp))
                if (state.kioskLockTaskDesired) {
                    MenuButton(label = "Выйти из киоска", filled = false, enabled = true, onClick = onExitKiosk)
                } else {
                    MenuButton(label = "Вернуть киоск", filled = true, enabled = true, onClick = onResumeKiosk)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuButton(
                    label = if (state.checkingUpdates) "Проверяю…" else "Проверить обновления",
                    filled = true,
                    enabled = !state.checkingUpdates && state.updateScreen == null,
                    onClick = onCheckUpdates
                )
                if (state.updateAvailable) {
                    MenuButton(
                        label = "Обновить",
                        filled = true,
                        enabled = !state.updateButtonBusy && state.updateScreen == null,
                        onClick = onStartUpdate
                    )
                }
                MenuButton(label = "Закрыть", filled = false, enabled = true, onClick = onDismiss)
            }
            if (state.kioskEnabled && !confirmClear) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Снять режим киоска",
                    color = AeroMuted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { confirmClear = true }
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun WifiDialog(
    wifi: WifiUiState,
    onDismiss: () -> Unit,
    onRescan: () -> Unit,
    onConnect: (WifiNetwork, String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var selected by remember { mutableStateOf<WifiNetwork?>(null) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .widthIn(min = 400.dp, max = 560.dp)
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AeroPanel)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(text = "WI-FI", color = AeroMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Сейчас: ${wifi.currentSsid ?: "не подключено"}",
                color = AeroSilver,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(16.dp))
            val chosen = selected
            if (chosen == null) {
                if (wifi.scanning) {
                    Text(text = "Ищу сети…", color = AeroMuted, fontSize = 14.sp)
                }
                wifi.networks.forEach { net ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selected = net
                                password = ""
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = net.ssid,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = if (net.ssid == wifi.currentSsid) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = (if (net.secured) "с паролем · " else "открытая · ") + "сигнал ${net.level + 1}/5",
                            color = AeroMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Text(text = chosen.ssid, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                if (chosen.secured) {
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        placeholder = { Text("Пароль сети") },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                            cursorColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = if (showPassword) "Скрыть пароль" else "Показать пароль",
                        color = AeroMuted,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { showPassword = !showPassword }
                            .padding(vertical = 10.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuButton(
                        label = if (wifi.connectingTo != null) "Подключаюсь…" else "Подключить",
                        filled = true,
                        enabled = wifi.connectingTo == null && (!chosen.secured || password.length >= 8),
                        onClick = { onConnect(chosen, password) }
                    )
                    MenuButton(label = "Другая сеть", filled = false, enabled = wifi.connectingTo == null, onClick = { selected = null })
                }
            }
            wifi.message?.let {
                Spacer(Modifier.height(12.dp))
                Text(text = it, color = if (wifi.error) AeroRed else AeroSilver, fontSize = 14.sp)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuButton(label = "Обновить список", filled = false, enabled = !wifi.scanning, onClick = onRescan)
                MenuButton(label = "Настройки Android", filled = false, enabled = true, onClick = onOpenSettings)
                MenuButton(label = "Закрыть", filled = false, enabled = true, onClick = onDismiss)
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
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
