package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag

enum class MainTab {
    SCANNER,
    HISTORY
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(MainTab.SCANNER) }

    val currentModal by viewModel.currentModal.collectAsState()
    val historyItems by viewModel.historyItems.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    // Status toast
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    // Back handling
    BackHandler(enabled = currentModal != null || currentTab != MainTab.SCANNER) {
        if (currentModal != null) {
            viewModel.dismissModal()
        } else if (currentTab != MainTab.SCANNER) {
            currentTab = MainTab.SCANNER
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == MainTab.SCANNER,
                    onClick = {
                        currentTab = MainTab.SCANNER
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scanner"
                        )
                    },
                    label = { Text("ස්කෑනරය (Scan)") },
                    modifier = Modifier.testTag("nav_scanner")
                )

                NavigationBarItem(
                    selected = currentTab == MainTab.HISTORY,
                    onClick = {
                        currentTab = MainTab.HISTORY
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (historyItems.isNotEmpty()) {
                                    Badge {
                                        Text(historyItems.size.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History"
                            )
                        }
                    },
                    label = { Text("ඉතිහාසය (History)") },
                    modifier = Modifier.testTag("nav_history")
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "tab_switch"
        ) { tab ->
            when (tab) {
                MainTab.SCANNER -> {
                    ScannerScreen(viewModel = viewModel)
                }
                MainTab.HISTORY -> {
                    HistoryScreen(viewModel = viewModel)
                }
            }
        }

        // Detail / Result Modal Bottom Sheet
        currentModal?.let { modalState ->
            ScanResultModal(
                modalState = modalState,
                onDismiss = { viewModel.dismissModal() },
                onQuickDelete = { viewModel.quickDeleteCurrentScan() },
                onUpdatePrice = { item, newPrice ->
                    viewModel.updatePrice(item, newPrice)
                }
            )
        }
    }
}
