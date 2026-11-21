package com.junjun.sideback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import com.junjun.sideback.ColorPickerDialog
import com.junjun.sideback.BackFxRenderer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.junjun.sideback.R
import com.junjun.sideback.BackFxConfig
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.ChipTab
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.PreferenceCategoryTitleType
import io.github.suqi8.coui.kmp.preference.SliderPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackFxPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = COUIScrollBehavior()

    var config by remember { mutableStateOf<BackFxConfig?>(null) }
    var saveFailed by remember { mutableStateOf(false) }
    var imageThumb by remember { mutableStateOf<Bitmap?>(null) }

    /** Points the preview at an edge; false leaves it centred. */
    var previewFixed by remember { mutableStateOf(true) }

    /**
     * Rule 2: the only writer. `BackFxConfig` is a mutable Java object with a no-argument `copy()`, so an edit
     * is copy → assign → save.
     */
    fun update(current: BackFxConfig?, change: (BackFxConfig) -> Unit) {
        val base = current ?: return
        val next = base.copy().also(change)
        config = next
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { next.save(context) }.getOrDefault(false) }
            if (!ok) saveFailed = true
        }
    }


    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val stored = withContext(Dispatchers.IO) { encodeImage(context, uri) }
                if (stored != null) {
                    imageThumb = withContext(Dispatchers.IO) { decodeThumb(stored) }
                    update(config) { cfg -> cfg.image = stored }
                } else {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.bf_image_failed),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    // Rule 1: read once, off the main thread, and never write on open.
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { BackFxConfig.load(context) }.getOrNull()
        }
        config = loaded
        previewFixed = loaded?.previewFixed ?: true
        imageThumb = withContext(Dispatchers.IO) {
            loaded?.image?.takeIf { it.isNotEmpty() }?.let(::decodeThumb)
        }
    }

    val current = config

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.bf_title),
                // Nothing sits behind this Activity — it is the module's launcher — so the arrow only repeated
                // the system back gesture.
                subtitle = stringResource(R.string.bf_subtitle),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { outerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = outerPadding.calculateTopPadding()),
        ) {
            // 「固定」 decides where the card lives, which is what the Java screen did by moving it between a
            // fixed holder and the scroll content. Pinned, it sits above the list and the settings scroll
            // beneath it; unpinned, it is the first item and scrolls away with them.
            if (current != null && previewFixed) {
                BackFxPreview(
                    config = current,
                    fixed = true,
                    onFixedChange = { fixedNow ->
                        previewFixed = fixedNow
                        update(current) { cfg -> cfg.previewFixed = fixedNow }
                    },
                )
            }

            LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                bottom = outerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            if (current == null) {
                item(key = "loading") { Caption(stringResource(R.string.bf_loading), large = true) }
                return@LazyColumn
            }

            // Rule 3: the preview is fed the in-memory config and a local flag.
            if (!previewFixed) {
                item(key = "preview") {
                    BackFxPreview(
                        config = current,
                        fixed = false,
                        onFixedChange = { fixedNow ->
                            previewFixed = fixedNow
                            update(current) { cfg -> cfg.previewFixed = fixedNow }
                        },
                    )
                }
            }

            item(key = "switch-title") {
                PreferenceCategory(
                    title = stringResource(R.string.bf_section_switch),
                    titleType = PreferenceCategoryTitleType.Medium,
                )
            }
            item(key = "enable") {
                Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    CardListGroup {
                        item {
                            SwitchPreference(
                                checked = current.enabled,
                                onCheckedChange = { value -> update(current) { it.enabled = value } },
                                title = stringResource(R.string.bf_enable_title),
                                summary = stringResource(R.string.bf_enable_summary),
                            )
                        }
                        item {
        ArrowPreference(
                                    title = stringResource(R.string.bf_reset),
                                    onClick = {
                                        // A fresh default object, saved through the same single writer.
                                        imageThumb = null
                                        update(current) { cfg ->
                                            val defaults = BackFxConfig()
                                            cfg.enabled = true
                                            cfg.icon = defaults.icon
                                            cfg.iconColor = defaults.iconColor
                                            cfg.iconScale = defaults.iconScale
                                            cfg.iconMirror = false
                                            cfg.iconOffsetDp = 0f
                                            cfg.image = ""
                                            cfg.iconEffect = defaults.iconEffect
                                            cfg.bgStyle = defaults.bgStyle
                                            cfg.bgColor = defaults.bgColor
                                            cfg.bgAlpha = defaults.bgAlpha
                                            cfg.border = true
                                            cfg.glassFrost = defaultFrost
                                        }
                                    },
                                )
                        }
                    }
                }
            }
            // Below the card, not inside it: as a row of the CardListGroup it read like another setting, with no
            // switch and no arrow. It is a caption about the group, so it sits under it.
            
            // Everything below the master switch is inert while it is off (skill §7.5).
            val on = current.enabled

            item(key = "iconShape-title") {
                PreferenceCategory(
                    title = stringResource(R.string.bf_group_icon_shape),
                    titleType = PreferenceCategoryTitleType.Medium,
                )
            }
            item(key = "iconShape-card") {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    CardListGroup {
                        item {
                            ChipChoiceRow(
                                labels = BackFxConfig.ICON_LABELS.toList(),
                                selectedIndex = BackFxConfig.ICON_VALUES.indexOf(current.icon).coerceAtLeast(0),
                                enabled = on,
                            ) { index -> update(current) { it.icon = BackFxConfig.ICON_VALUES[index] } }
                        }
                        if (current.icon == BackFxConfig.ICON_IMAGE) {
                            item {
                                ArrowPreference(
                                    title = stringResource(R.string.bf_image_title),
                                summary = if (current.image.isEmpty()) {
                                    stringResource(R.string.bf_image_empty)
                                } else {
                                    stringResource(R.string.bf_image_set)
                                },
                                enabled = on,
                                onClick = { pickImage.launch("image/*") },
                                startAction = imageThumb?.let { thumb ->
                                    { Thumb(thumb) }
                                },
                                    endActions = {
                                        if (current.image.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    imageThumb = null
                                                    update(current) { it.image = "" }
                                                },
                                                enabled = on,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = stringResource(
                                                        R.string.bf_image_clear,
                                                    ),
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                                                if (current.icon != BackFxConfig.ICON_NONE) {
item {
                            SwitchPreference(
                                checked = current.iconMirror,
                                onCheckedChange = { value -> update(current) { it.iconMirror = value } },
                                title = stringResource(R.string.bf_mirror_title),
                                summary = stringResource(R.string.bf_mirror_summary),
                                enabled = on,
                            )
                        }
                        }
                        // The colour paints a vector glyph; with a picture chosen the renderer draws the bitmap
                        // and never reads iconColor, so the row would be a control with nothing to affect.
                        if (current.icon != BackFxConfig.ICON_IMAGE) {
                        item {
                            ColourField(
                                label = stringResource(R.string.bf_icon_or_track_color),
                                color = current.iconColor,
                                enabled = on,
                                onPick = {
                                    ColorPickerDialog.show(context, current.iconColor) { argb ->
                                        update(current) { it.iconColor = argb }
                                    }
                                },
                            )
                        }
                        }
                    }
                }
            }
            if (current.icon != BackFxConfig.ICON_NONE) { item(key = "iconSize-title") {
                PreferenceCategory(
                    title = stringResource(R.string.bf_group_icon_size),
                    titleType = PreferenceCategoryTitleType.Medium,
                )
            }
            }
            if (current.icon != BackFxConfig.ICON_NONE) { item(key = "iconSize-card") {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    CardListGroup {
                        item {
                            SliderPreference(
                                value = BackFxConfig.percentForIconScale(current.iconScale).toFloat(),
                                onValueChange = { percent ->
                                    val rounded = percent.toInt()
                                    if (rounded != BackFxConfig.percentForIconScale(current.iconScale)) {
                                        update(current) { it.iconScale = BackFxConfig.iconScaleForPercent(rounded) }
                                    }
                                },
                                title = stringResource(R.string.bf_icon_size),
                                valueText = stringResource(
                                    R.string.bf_icon_size_value,
                                    BackFxConfig.percentForIconScale(current.iconScale),
                                    current.iconScale * BackFxConfig.ICON_BASE_DP,
                                ),
                                valueRange = 0f..100f,
                                enabled = on,
                            )
                        }
                        item {
                            SliderPreference(
                                value = current.iconOffsetDp,
                                onValueChange = { value ->
                                    if (value != current.iconOffsetDp) {
                                        update(current) { it.iconOffsetDp = value }
                                    }
                                },
                                title = stringResource(R.string.bf_icon_offset),
                                valueText = stringResource(
                                    R.string.bf_icon_offset_value,
                                    current.iconOffsetDp.toInt(),
                                ),
                                valueRange = 0f..40f,
                                enabled = on,
                            )
                        }
                    }
                }
            }
            }
            item(key = "iconEffect-title") {
                PreferenceCategory(
                    title = stringResource(R.string.bf_group_icon_effect),
                    titleType = PreferenceCategoryTitleType.Medium,
                )
            }
            item(key = "iconEffect-card") {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    CardListGroup {
                                                // No icon to put a halo round.
                        if (current.icon != BackFxConfig.ICON_NONE) {
item {
                            // 光晕 is its own switch: with it missing the halo was always on.
                            SwitchPreference(
                                checked = current.glow,
                                onCheckedChange = { value -> update(current) { it.glow = value } },
                                title = stringResource(R.string.bf_glow_title),
                                summary = stringResource(R.string.bf_glow_summary),
                                enabled = on,
                            )
                        }
                        }
                                                // 残影 and 拉伸 both animate the icon itself.
                        if (current.icon != BackFxConfig.ICON_NONE) {
item {
                            // Both rows write `iconEffect`, so they behave as one exclusive choice: a value from
                            // the other row leaves this one showing 无.
                            OverlayDropdownPreference(
                                items = BackFxConfig.ICON_EFFECT_LABELS.toList(),
                                selectedIndex = BackFxConfig.ICON_EFFECT_VALUES
                                    .indexOf(current.iconEffect)
                                    .coerceAtLeast(0),
                                title = stringResource(R.string.bf_effect_icon),
                                onSelectedIndexChange = { index: Int ->
                                    update(current) { it.iconEffect = BackFxConfig.ICON_EFFECT_VALUES[index] }
                                },
                                enabled = on,
                            )
                        }
                        }
                        item {
                            OverlayDropdownPreference(
                                items = BackFxConfig.TRACK_EFFECT_LABELS.toList(),
                                selectedIndex = BackFxConfig.TRACK_EFFECT_VALUES
                                    .indexOf(current.iconEffect)
                                    .coerceAtLeast(0),
                                title = stringResource(R.string.bf_effect_track),
                                onSelectedIndexChange = { index: Int ->
                                    update(current) { it.iconEffect = BackFxConfig.TRACK_EFFECT_VALUES[index] }
                                },
                                enabled = on,
                            )
                        }                    }
                }
            }
            item(key = "bgShape-title") {
                PreferenceCategory(
                    title = stringResource(R.string.bf_group_bg_shape),
                    titleType = PreferenceCategoryTitleType.Medium,
                )
            }
            item(key = "bgShape-card") {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    CardListGroup {
                        item {
                            ChipChoiceRow(
                                labels = BackFxConfig.BG_LABELS.toList(),
                                selectedIndex = BackFxConfig.BG_VALUES.indexOf(current.bgStyle).coerceAtLeast(0),
                                enabled = on,
                            ) { index -> update(current) { it.bgStyle = BackFxConfig.BG_VALUES[index] } }
                        }
                        // 无 draws nothing, so there is no fill to colour and no opacity to set. The two rows were
                        // left outside the guard that already covers 背景大小 and 背景特效.
                        if (current.bgStyle != BackFxConfig.BG_NONE) {
                            item {
                                ColourField(
                                    label = stringResource(R.string.bf_bg_color),
                                    color = current.bgColor,
                                    enabled = on,
                                    onPick = {
                                        ColorPickerDialog.show(context, current.bgColor) { argb ->
                                            update(current) { it.bgColor = argb }
                                        }
                                    },
                                )
                            }
                            item {
                                // Transparency, not opacity: 0 is fully opaque, 100 is invisible. The config stores
                                // bgAlpha as 0..255 where larger is more opaque, so the row flips it and the stored
                                // meaning stays as the renderer expects.
                                SliderPreference(
                                    value = (100 - (current.bgAlpha * 100 / 255)).toFloat(),
                                    onValueChange = { value ->
                                        val transparency = value.toInt().coerceIn(0, 100)
                                        val opaque = (255 * (100 - transparency) / 100)
                                        if (opaque != current.bgAlpha) {
                                            update(current) { it.bgAlpha = opaque }
                                        }
                                    },
                                    title = stringResource(R.string.bf_transparency),
                                    valueText = stringResource(
                                        R.string.bf_alpha_value,
                                        100 - (current.bgAlpha * 100 / 255),
                                    ),
                                    valueRange = 0f..100f,
                                    enabled = on,
                                )
                            }
                        }
                        if (current.bgStyle == BackFxConfig.BG_GLASS) {
                            item {
                                SliderPreference(
                                    value = current.glassFrost.toFloat(),
                                    onValueChange = { value ->
                                        val frost = value.toInt().coerceIn(0, 100)
                                        if (frost != current.glassFrost) {
                                            update(current) { it.glassFrost = frost }
                                        }
                                    },
                                    title = stringResource(R.string.bf_frost),
                                    valueText = stringResource(R.string.bf_alpha_value, current.glassFrost),
                                    valueRange = 0f..100f,
                                    enabled = on,
                                )
                            }
                        }
                        if (current.bgStyle == BackFxConfig.BG_GRADIENT) {
                            item {
                                ChipChoiceRow(
                                    labels = BackFxConfig.GRAD_LABELS.toList(),
                                    selectedIndex = BackFxConfig.GRAD_VALUES
                                        .indexOf(current.gradientType)
                                        .coerceAtLeast(0),
                                    enabled = on,
                                    label = stringResource(R.string.bf_grad_type),
                                ) { index ->
                                    update(current) { it.gradientType = BackFxConfig.GRAD_VALUES[index] }
                                }
                            }
                            // The third stop. `buildGradient` in the renderer switches to three colours as soon
                            // as this one has an alpha, so this switch is what enables that path.
                            item {
                                SwitchPreference(
                                    checked = (current.gradientMidColor ushr 24) != 0,
                                    onCheckedChange = { value ->
                                        update(current) {
                                            it.gradientMidColor = if (value) 0xFFFF7EB6.toInt() else 0
                                        }
                                    },
                                    title = stringResource(R.string.bf_grad_third),
                                    summary = stringResource(R.string.bf_grad_third_summary),
                                    enabled = on,
                                )
                            }
                            if ((current.gradientMidColor ushr 24) != 0) {
                                item {
                                    ColourField(
                                        label = stringResource(R.string.bf_grad_mid_color),
                                        color = current.gradientMidColor,
                                        enabled = on,
                                        onPick = {
                                            ColorPickerDialog.show(
                                                context,
                                                current.gradientMidColor,
                                            ) { argb -> update(current) { it.gradientMidColor = argb } }
                                        },
                                    )
                                }
                            }
                            item {
                                SliderPreference(
                                    value = current.gradientAngle.toFloat(),
                                    onValueChange = { value ->
                                        val angle = value.toInt().coerceIn(0, 360)
                                        if (angle != current.gradientAngle) {
                                            update(current) { it.gradientAngle = angle }
                                        }
                                    },
                                    title = stringResource(R.string.bf_grad_angle),
                                    valueText = stringResource(R.string.bf_angle_value, current.gradientAngle),
                                    valueRange = 0f..360f,
                                    enabled = on,
                                )
                            }
                            item {
                                SliderPreference(
                                    value = current.gradientMidPos * 100f,
                                    onValueChange = { value ->
                                        val pos = (value.toInt().coerceIn(0, 100)) / 100f
                                        if (pos != current.gradientMidPos) {
                                            update(current) { it.gradientMidPos = pos }
                                        }
                                    },
                                    title = stringResource(R.string.bf_grad_mid),
                                    valueText = stringResource(
                                        R.string.bf_alpha_value,
                                        (current.gradientMidPos * 100).toInt(),
                                    ),
                                    valueRange = 0f..100f,
                                    enabled = on,
                                )
                            }
                        }
                    }
                }
            }
            if (current.bgStyle != BackFxConfig.BG_NONE) { item(key = "bgSize-title") {
                PreferenceCategory(
                    title = stringResource(R.string.bf_group_bg_size),
                    titleType = PreferenceCategoryTitleType.Medium,
                )
            }
            }
            if (current.bgStyle != BackFxConfig.BG_NONE) { item(key = "bgSize-card") {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    CardListGroup {
                        item {
                            // The renderer clamps bgRadius to 40..130 and works with any value in between; the two
                            // chips left most of that range unreachable.
                            SliderPreference(
                                value = current.bgRadius.toFloat(),
                                onValueChange = { value ->
                                    val radius = value.toInt().coerceIn(40, 130)
                                    if (radius != current.bgRadius) update(current) { it.bgRadius = radius }
                                },
                                title = stringResource(R.string.bf_drop_size),
                                valueText = stringResource(R.string.bf_drop_size_value, current.bgRadius),
                                valueRange = 40f..130f,
                                enabled = on,
                            )
                        }
                        item {
                            // How pointed the outline is: `bgCurve` scales the drop's two cubic control points
                            // together, so 50 reproduces the shape the screen has always drawn.
                            SliderPreference(
                                value = current.bgCurve.toFloat(),
                                onValueChange = { value ->
                                    val curve = value.toInt().coerceIn(0, 100)
                                    if (curve != current.bgCurve) update(current) { it.bgCurve = curve }
                                },
                                title = stringResource(R.string.bf_drop_curve),
                                valueText = stringResource(R.string.bf_alpha_value, current.bgCurve),
                                valueRange = 0f..100f,
                                enabled = on,
                            )
                        }
                        item {
                            // The cap on how far the drop may reach. The gesture still tracks the finger up to
                            // this distance; it was a hardcoded 56dp before.
                            SliderPreference(
                                value = current.edgeTravelDp.toFloat(),
                                onValueChange = { value ->
                                    val travel = value.toInt().coerceIn(10, 100)
                                    if (travel != current.edgeTravelDp) {
                                        update(current) { it.edgeTravelDp = travel }
                                    }
                                },
                                title = stringResource(R.string.bf_edge_travel),
                                valueText = stringResource(R.string.bf_drop_size_value, current.edgeTravelDp),
                                valueRange = 10f..100f,
                                enabled = on,
                            )
                        }
                                            }
                }
            }
            }
            if (current.bgStyle != BackFxConfig.BG_NONE) { item(key = "bgEffect-title") {
                PreferenceCategory(
                    title = stringResource(R.string.bf_group_bg_effect),
                    titleType = PreferenceCategoryTitleType.Medium,
                )
            }
            }
            if (current.bgStyle != BackFxConfig.BG_NONE) { item(key = "bgEffect-card") {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    CardListGroup {
                        item {
                            SwitchPreference(
                                checked = current.border,
                                onCheckedChange = { value -> update(current) { it.border = value } },
                                title = stringResource(R.string.bf_border_title),
                                summary = stringResource(R.string.bf_border_summary),
                                enabled = on,
                            )
                        }                    }
                }
            }
            }

                if (saveFailed) {
                    item(key = "save-failed") { Caption(stringResource(R.string.bf_save_failed)) }
                }
            }
        }
    }
}

/** The Java screen's default frost, which is what a reset should restore. */
private const val defaultFrost = 55

/**
 * The preview is the Java screen's own view, hosted in Compose.
 *
 * It is deliberately not reimplemented here. `BackFxPreview` already owns the scaling that makes a preview
 * readable — it maps the view onto a virtual 360dp screen and passes that scaled density to the renderer — and
 * it animates a demonstration slide so trail and particle effects are visible without swiping a real edge.
 * Two attempts at writing this again in Compose both produced a wrong picture: an invented capsule first, then
 * the real renderer at the wrong scale.
 */
@Composable
private fun BackFxPreview(
    config: BackFxConfig,
    fixed: Boolean,
    onFixedChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.bf_preview))
                }
                // A minimum gap: the label and the switch were sitting against each other.
                Text(
                    text = stringResource(R.string.bf_preview_fixed),
                    modifier = Modifier.padding(end = 10.dp),
                )
                Switch(checked = fixed, onCheckedChange = onFixedChange)
            }
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    BackFxPreview(ctx).apply {
                        setConfig(config)
                        // Tapping re-runs the entry animation, as the Java screen did.
                        setOnClickListener { pulse() }
                    }
                },
                update = { view -> view.setConfig(config) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(360f / 326.8f)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}


/**
 * One colour: a swatch showing the current value, and a 选取 button that opens the picker.
 *
 * The rows used to list eight presets inline, which made them long, pushed the + off the edge on a narrow screen,
 * and never showed which colour was actually in effect.
 */
@Composable
private fun ColourField(
    label: String,
    color: Int,
    enabled: Boolean,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(color), CircleShape)
                .border(1.dp, COUITheme.colorScheme.labelPrimary.copy(alpha = 0.2f), CircleShape),
        )
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(COUITheme.colorScheme.labelPrimary.copy(alpha = 0.08f))
                .clickable(enabled = enabled) { onPick() }
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(text = stringResource(R.string.bf_pick_colour))
        }
    }
}

@Composable
private fun ChipChoiceRow(
    labels: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    label: String? = null,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (label != null) {
            Text(text = label, modifier = Modifier.padding(bottom = 8.dp))
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { index, text ->
                val selected = index == selectedIndex
                // COUI's ChipTab marks selection with a barely-brighter capsule on a light surface, so the
                // chosen entry was unreadable at a glance. The selected chip is filled instead — dark fill,
                // light label — the way the Java screen's chips worked.
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                COUITheme.colorScheme.labelPrimary
                            } else {
                                COUITheme.colorScheme.labelPrimary.copy(alpha = 0.06f)
                            },
                        )
                        .clickable(enabled = enabled) { onSelect(index) }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                ) {
                    // The fill is labelPrimary, so the label has to be its opposite: painting both in
                    // labelPrimary gave a solid black pill with black text on it.
                    Text(
                        text = text,
                        color = if (selected) {
                            COUITheme.colorScheme.surface
                        } else {
                            COUITheme.colorScheme.labelPrimary
                        },
                    )
                }
            }
        }
    }
}

/**
 * The colour row.
 *
 * The swatches are clickable — the first version took an `onSelect` and never called it, which is why picking
 * a colour did nothing. The trailing `+` opens the Java screen's own `ColorPickerDialog`.
 *
 * The row wraps: eight 38dp swatches plus gaps is wider than the card, and the `+` ended up laid out past the
 * right edge where it could not be tapped.
 *
 * Selection is a ring drawn outside the swatch, since the old mark — a thicker border on the dot itself —
 * vanished against black and white.
 */
@Composable
private fun ColourDots(
    colors: IntArray,
    selected: Int,
    enabled: Boolean,
    label: String,
    allowCustom: Boolean,
    onCustom: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text = label)
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            colors.forEach { argb ->
                val isSelected = argb == selected
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, COUITheme.colorScheme.labelPrimary, CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable(enabled = enabled) { onSelect(argb) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(argb), CircleShape)
                            .then(
                                if (isSelected) Modifier else Modifier.border(
                                    1.dp,
                                    COUITheme.colorScheme.labelPrimary.copy(alpha = 0.15f),
                                    CircleShape,
                                ),
                            ),
                    )
                }
            }
            if (allowCustom) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(1.dp, COUITheme.colorScheme.labelPrimary.copy(alpha = 0.35f), CircleShape)
                        .clickable(enabled = enabled) { onCustom() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "+")
                }
            }
        }
    }
}

@Composable
private fun Thumb(bitmap: Bitmap) {
    androidx.compose.foundation.Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp)),
    )
}

/**
 * Re-encodes a picked image into the base64 the config stores.
 *
 * Downscaled to 512px first, as the Java screen did: the config travels through `Settings.Global` and a
 * preferences file, both of which are a poor home for a photograph.
 */
private fun encodeImage(context: Context, uri: Uri): String? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) {
        sample *= 2
    }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val bytes = java.io.ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
    Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
}.getOrNull()

/** Decodes a stored image for the thumbnail and the preview. */
private fun decodeThumb(stored: String): Bitmap? = runCatching {
    val bytes = Base64.decode(stored, Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

/** A caption line, matching the toolbox's own helper for the two styles this page uses. */
@Composable
private fun Caption(
    text: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    horizontalPadding: Dp = 32.dp,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = horizontalPadding, vertical = 6.dp),
        style = if (large) COUITheme.textStyles.bodyM else COUITheme.textStyles.caption,
        color = if (large) {
            COUITheme.colorScheme.labelPrimary
        } else {
            COUITheme.colorScheme.labelSecondary
        },
    )
}
