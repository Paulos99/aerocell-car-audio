package com.aerocell.stand.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerocell.stand.upload.QrEncoder
import com.aerocell.stand.upload.UploadUiState
import com.aerocell.stand.ui.theme.AeroMuted
import com.aerocell.stand.ui.theme.AeroPanel
import com.aerocell.stand.ui.theme.AeroRedGlow

@Composable
fun OwnTrackModal(
    state: UploadUiState,
    onClose: () -> Unit
) {
    if (!state.modalOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AeroPanel)
                .clickable(onClick = {})
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Свой трек",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = modalMessage(state),
                color = AeroMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth()
            )
            val url = state.uploadUrl
            if (url != null && !state.serverFailed) {
                Spacer(Modifier.height(18.dp))
                val qr = remember(url) { QrEncoder.toBitmap(url, 640).asImageBitmap() }
                Image(
                    bitmap = qr,
                    contentDescription = "QR для загрузки трека",
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = url,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text("Закрыть", color = AeroRedGlow, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

internal fun modalMessage(state: UploadUiState): String = when {
    state.justUploaded ->
        "Трек уже на стенде. Можно слушать и двигать баланс. Если нужен другой — отсканируйте новый QR."
    state.uploading ->
        "Файл уже загружается, подождите немного."
    state.serverFailed ->
        "Не получилось связаться с облаком. Проверьте интернет на планшете и попробуйте ещё раз."
    state.uploadUrl == null ->
        "Готовим ссылку, подождите немного."
    else ->
        "Наведите камеру на QR и откройте ссылку. К Wi-Fi стенда подключаться не нужно — хватит обычного интернета."
}
