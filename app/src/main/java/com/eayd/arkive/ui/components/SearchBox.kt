package com.eayd.arkive.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eayd.arkive.i18n.Txt

/** Ana ekrandaki arama kutusu. Yazı yazılmaz; dokununca arama sayfası açılır. */
@Composable
fun SearchBox(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SearchFrame(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClickLabel = Txt.search.title, role = Role.Button, onClick = onClick),
        focused = false,
    ) {
        SearchHint()
    }
}

/** Arama sayfasındaki yazı alanı; ana ekrandaki kutuyla aynı görünür. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { field ->
            SearchFrame(
                modifier = Modifier.fillMaxWidth(),
                focused = focused,
                trailing = if (value.isEmpty()) null else {
                    {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Filled.Close, contentDescription = Txt.search.clear)
                        }
                    }
                },
            ) {
                if (value.isEmpty()) SearchHint()
                field()
            }
        },
    )
}

/** Kutunun çerçevesi: yuvarlak kenar, solda büyüteç, ortada yazı, varsa sağda düğme. */
@Composable
private fun SearchFrame(
    modifier: Modifier,
    focused: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    Row(
        modifier = modifier
            .height(48.dp)
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.primary else colors.outline, shape)
            .padding(start = 14.dp, end = if (trailing == null) 16.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { content() }
        trailing?.invoke()
    }
}

/** Kutu boşken görünen yazı. */
@Composable
private fun SearchHint() {
    Text(
        text = Txt.search.hint,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
