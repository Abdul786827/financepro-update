package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.navigation.AppScreen
import kotlinx.coroutines.flow.first

data class BottomNavItem(
    val screen: AppScreen,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

val financeBottomNavItems: List<BottomNavItem> = listOf(
    BottomNavItem(AppScreen.DASHBOARD, "Dashboard", Icons.Default.Dashboard, "nav_dashboard"),
    BottomNavItem(AppScreen.DAILY_STATEMENT, "Statement", Icons.Default.TableChart, "nav_statement"),
    BottomNavItem(AppScreen.ACCOUNTS, "Accounts", Icons.Default.AccountBalance, "nav_accounts"),
    BottomNavItem(AppScreen.PETROL_EXTRA, "Fuel/Extra", Icons.Default.LocalGasStation, "nav_petrol_extra"),
    BottomNavItem(AppScreen.STATEMENTS, "Monthly", Icons.Default.CalendarMonth, "nav_statements"),
    BottomNavItem(AppScreen.UDHAAR, "Udhaar", Icons.Default.Handshake, "nav_udhaar"),
    BottomNavItem(AppScreen.SAVINGS, "Savings", Icons.Default.Savings, "nav_savings")
)

fun buildFinanceBottomNavItems(showTax: Boolean): List<BottomNavItem> {
    return if (showTax) {
        financeBottomNavItems + BottomNavItem(
            screen = AppScreen.TAX,
            label = "Tax",
            icon = Icons.Default.AssuredWorkload,
            testTag = "nav_tax"
        )
    } else {
        financeBottomNavItems
    }
}

@Composable
fun ScrollableFinanceBottomBar(
    currentScreen: AppScreen,
    onSelectScreen: (AppScreen) -> Unit,
    modifier: Modifier = Modifier,
    items: List<BottomNavItem> = financeBottomNavItems,
    itemWidth: Dp = 80.dp, // 360dp / 80dp = 4.5 items visible comfortably on 360dp phones
    listState: LazyListState = rememberLazyListState()
) {
    val selectedIndex = remember(currentScreen, items) {
        items.indexOfFirst { it.screen == currentScreen }
    }

    // Automatically scroll to the selected item whenever selection changes and keep it fully visible
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect

        val layoutInfo = if (listState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
            listState.layoutInfo
        } else {
            snapshotFlow { listState.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
        }

        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = layoutInfo.viewportEndOffset
        val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == selectedIndex }

        when {
            visibleItem == null -> {
                listState.animateScrollToItem(selectedIndex)
                val updated = listState.layoutInfo
                val nowVisible = updated.visibleItemsInfo.find { it.index == selectedIndex }
                if (nowVisible != null && nowVisible.offset + nowVisible.size > updated.viewportEndOffset) {
                    val delta = (nowVisible.offset + nowVisible.size - updated.viewportEndOffset).toFloat()
                    listState.animateScrollBy(delta)
                }
            }
            visibleItem.offset < viewportStart -> {
                val delta = (visibleItem.offset - viewportStart).toFloat()
                listState.animateScrollBy(delta)
            }
            visibleItem.offset + visibleItem.size > viewportEnd -> {
                val delta = (visibleItem.offset + visibleItem.size - viewportEnd).toFloat()
                listState.animateScrollBy(delta)
            }
        }
    }

    Surface(
        color = NavigationBarDefaults.containerColor,
        tonalElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("bottom_nav_bar")
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(min = 80.dp)
                .testTag("bottom_nav_lazy_row"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(items, key = { _, item -> item.screen.name }) { _, item ->
                val isSelected = currentScreen == item.screen
                ScrollableBottomNavItemView(
                    item = item,
                    isSelected = isSelected,
                    itemWidth = itemWidth,
                    onClick = { onSelectScreen(item.screen) }
                )
            }
        }
    }
}

@Composable
private fun ScrollableBottomNavItemView(
    item: BottomNavItem,
    isSelected: Boolean,
    itemWidth: Dp,
    onClick: () -> Unit
) {
    val pillColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "nav_pill_color"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 180),
        label = "nav_icon_color"
    )
    val labelColor = if (isSelected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .width(itemWidth)
            .heightIn(min = 80.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                role = Role.Tab,
                onClick = onClick
            )
            .semantics {
                selected = isSelected
                contentDescription = item.label
            }
            .padding(horizontal = 2.dp, vertical = 10.dp)
            .testTag(item.testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Selected-item highlight pill matching M3 NavigationBar style
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(pillColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.label,
            color = labelColor,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${item.testTag}_label")
        )
    }
}
