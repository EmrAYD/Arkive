package com.eayd.arkive.ui.components

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.eayd.arkive.ai.SpeechProblem
import com.eayd.arkive.ai.SpeechState
import com.eayd.arkive.ai.SpeechStatus
import com.eayd.arkive.ai.SpeechText
import com.eayd.arkive.ai.SummaryPlayback
import com.eayd.arkive.ai.SummarySpeaker
import com.eayd.arkive.i18n.AppLanguage
import com.eayd.arkive.i18n.Lang
import com.eayd.arkive.i18n.Txt

/**
 * Özet penceresinin seslendiricisi. Bu özet zaten okunuyorsa okuma kaldığı yerden görünür.
 *
 * Pencere kapanınca hiç başlatılmamış seslendirici kapatılır; okunan özet bildirimden yönetilmeye devam eder.
 */
@Composable
fun rememberSummarySpeaker(fileId: String, fileName: String, text: String, note: String?): SummarySpeaker {
    val context = LocalContext.current
    val speaker = remember(fileId, text) { SummaryPlayback.speakerFor(context, fileId, fileName, text, note) }
    DisposableEffect(speaker) { onDispose { SummaryPlayback.detach(speaker) } }
    return speaker
}

/** Okunan cümle; okuma sürmüyorsa ya da duraklatılmamışsa null. */
fun SummarySpeaker.currentRange(state: SpeechState): IntRange? =
    if (state.status == SpeechStatus.Playing || state.status == SpeechStatus.Paused) sentences.getOrNull(state.sentence) else null

/** Metni, okunan cümlesi vurgulanmış olarak döner. */
fun highlightSentence(text: String, range: IntRange?, background: Color, foreground: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        if (range != null && range.first >= 0 && range.last < text.length) {
            addStyle(SpanStyle(background = background, color = foreground), range.first, range.last + 1)
        }
    }

/** Okunan cümle görünmüyorsa metni ona kaydırır. [viewport] görünen alanın yüksekliği (piksel). */
suspend fun ScrollState.reveal(layout: TextLayoutResult, range: IntRange, viewport: Int) {
    if (range.last >= layout.layoutInput.text.length) return
    val top = layout.getLineTop(layout.getLineForOffset(range.first)).toInt()
    val bottom = layout.getLineBottom(layout.getLineForOffset(range.last)).toInt()
    if (top < value || (viewport > 0 && bottom > value + viewport)) {
        animateScrollTo((top - REVEAL_MARGIN_PX).coerceAtLeast(0))
    }
}

private const val REVEAL_MARGIN_PX = 24

/** Özet penceresindeki oynatıcı: ortada oynat/duraklat, sağda okuma hızı. */
@Composable
fun SummaryPlayer(speaker: SummarySpeaker, state: SpeechState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val problem = state.problem
    val usable = state.status != SpeechStatus.Unavailable || problem == SpeechProblem.Failed
    Column(modifier.fillMaxWidth()) {
        if (problem != null) {
            Text(
                text = problemText(problem, speaker.language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (problem == SpeechProblem.NoVoice) {
                TextButton(onClick = { openVoiceInstall(context) }) { Text(Txt.speech.installVoice) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            FilledIconButton(onClick = speaker::toggle, enabled = usable) {
                when (state.status) {
                    SpeechStatus.Preparing -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).semantics { contentDescription = Txt.speech.preparing },
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    SpeechStatus.Playing -> Icon(Icons.Filled.Pause, contentDescription = Txt.speech.pause)
                    else -> Icon(Icons.Filled.PlayArrow, contentDescription = Txt.speech.listen)
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                val speed = SpeechText.speedText(state.speed, Lang.locale)
                TextButton(
                    onClick = speaker::cycleSpeed,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.semantics { contentDescription = Txt.speech.speed(speed) },
                ) {
                    Text(speed, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Seslendirme yapılamıyorsa kullanıcıya gösterilen açıklama. */
private fun problemText(problem: SpeechProblem, language: AppLanguage): String = when (problem) {
    SpeechProblem.NoEngine -> Txt.speech.noEngine
    SpeechProblem.NoVoice -> Txt.speech.noVoice(languageName(language))
    SpeechProblem.Failed -> Txt.speech.failed
}

/** Ses paketinin dili, uygulamanın dilinde. */
private fun languageName(language: AppLanguage): String = when (language) {
    AppLanguage.Turkish -> Txt.speech.turkish
    AppLanguage.English -> Txt.speech.english
}

/** Ses paketini indirme ekranını açar; açılamazsa metin okuma ayarlarını. */
private fun openVoiceInstall(context: Context) {
    val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val settings = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(install) }.onFailure { runCatching { context.startActivity(settings) } }
}
