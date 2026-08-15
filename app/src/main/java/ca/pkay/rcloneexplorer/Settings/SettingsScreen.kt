package ca.pkay.rcloneexplorer.Settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.pkay.rcloneexplorer.BuildConfig
import ca.pkay.rcloneexplorer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onCategoryClick: (Int) -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onAboutClick: () -> Unit = {},
    showBackButton: Boolean = true,
    onBackPressed: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Preferences Section
            SettingsCategoryHeader(title = "App Preferences")

            SettingsCard(
                icon = Icons.Outlined.Tune,
                iconTint = Color(0xFF3B82F6),
                title = "General Settings",
                subtitle = "Default folder, shortcuts, startup behaviors",
                onClick = { onCategoryClick(SettingsFragment.GENERAL_SETTINGS) }
            )

            SettingsCard(
                icon = Icons.Outlined.Palette,
                iconTint = Color(0xFF8B5CF6),
                title = "Look and Feel",
                subtitle = "App themes, OLED dark mode, icon sizes",
                onClick = { onCategoryClick(SettingsFragment.LOOK_AND_FEEL_SETTINGS) }
            )

            SettingsCard(
                icon = Icons.Outlined.FolderShared,
                iconTint = Color(0xFF06B6D4),
                title = "File Access & SAF",
                subtitle = "Local storage integration, SAF provider, WebDAV bridges",
                onClick = { onCategoryClick(SettingsFragment.FILE_ACCESS_SETTINGS) }
            )

            SettingsCard(
                icon = Icons.Outlined.Notifications,
                iconTint = Color(0xFFF59E0B),
                title = "Notifications & Reports",
                subtitle = "Sync alerts, background progress updates",
                onClick = { onCategoryClick(SettingsFragment.NOTIFICATION_SETTINGS) }
            )

            SettingsCard(
                icon = Icons.Outlined.Terminal,
                iconTint = Color(0xFF10B981),
                title = "Diagnostic Logs",
                subtitle = "View application error logs and sync traces",
                onClick = { onCategoryClick(SettingsFragment.LOGGING_SETTINGS) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Configuration Backup Section
            SettingsCategoryHeader(title = "Configuration & Remotes")

            SettingsCard(
                icon = Icons.Outlined.FileDownload,
                iconTint = Color(0xFF38BDF8),
                title = "Import Configuration",
                subtitle = "Restore rclone remote configs and credentials",
                onClick = onImportClick
            )

            SettingsCard(
                icon = Icons.Outlined.FileUpload,
                iconTint = Color(0xFF6366F1),
                title = "Export Configuration",
                subtitle = "Backup encrypted remote configs to a safe location",
                onClick = onExportClick
            )

            Spacer(modifier = Modifier.height(4.dp))

            // About Neubofy Remote Manager Card
            SettingsCategoryHeader(title = "About & Community")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onAboutClick),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "App Logo",
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Remote Manager",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Version ${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "View about",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Developed by Pawan Vasudev (@pawanwashudev). Empowered by Rclone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
}

@Composable
fun SettingsCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = iconTint.copy(alpha = 0.12f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier
                            .padding(11.dp)
                            .fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
