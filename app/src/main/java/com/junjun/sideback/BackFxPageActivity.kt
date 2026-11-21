package com.junjun.sideback

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.junjun.sideback.BackFxPage
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeController

/**
 * 侧滑返回, as its own Activity so predictive back reveals the home screen underneath.
 */
class BackFxPageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val controller = ThemeController(ColorSchemeMode.System)
        setContent {
            val dispatcherOwner = rememberNavigationEventDispatcherOwner(parent = null)
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) {
                COUITheme(controller = controller) {
                    BackFxPage()
                }
            }
        }
        PageAnim.slideIn(this, findViewById(android.R.id.content))
    }
}
