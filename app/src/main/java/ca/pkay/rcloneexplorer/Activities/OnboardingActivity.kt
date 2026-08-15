package ca.pkay.rcloneexplorer.Activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.util.PermissionManager
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {

    companion object {
        private const val INTRO_COMPLETED_KEY = "intro_v2_5_2_completed"

        fun completedIntro(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(INTRO_COMPLETED_KEY, false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF3B82F6),
                    secondary = Color(0xFF38BDF8),
                    surface = Color(0xFF0F172A),
                    surfaceVariant = Color(0xFF1E293B),
                    background = Color(0xFF0B1120),
                    onBackground = Color(0xFFF8FAFC),
                    onSurface = Color(0xFFF8FAFC)
                )
            ) {
                OnboardingScreen(
                    onFinish = {
                        PreferenceManager.getDefaultSharedPreferences(this)
                            .edit()
                            .putBoolean(INTRO_COMPLETED_KEY, true)
                            .apply()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 5 })
    val coroutineScope = rememberCoroutineScope()
    val permissionManager = remember { PermissionManager(context) }

    var hasStoragePerm by remember {
        mutableStateOf(permissionManager.grantedStorage())
    }
    var hasNotifPerm by remember {
        mutableStateOf(permissionManager.grantedNotifications())
    }

    val requestStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasStoragePerm = permissionManager.grantedStorage()
    }

    val requestNotifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotifPerm = isGranted || permissionManager.grantedNotifications()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0B1120),
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Progress Indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(5) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(6.dp)
                            .width(if (isSelected) 32.dp else 10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            // Pager Pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingSlide(
                        iconRes = R.drawable.app_logo,
                        title = "Welcome to Remote Manager",
                        subtitle = "Fast, encrypted, seamless cloud and remote file management powered by rclone.",
                        badgeText = "Powered by Neubofy"
                    )
                    1 -> OnboardingPermissionSlide(
                        icon = Icons.Default.FolderOpen,
                        title = "Full File & Storage Access",
                        description = "Remote Manager requires storage permission to transfer, cache, sync, and manage files on your device.",
                        isGranted = hasStoragePerm,
                        buttonText = if (hasStoragePerm) "Access Granted" else "Grant Storage Permission",
                        onGrantClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    requestStorageLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                                }
                            } else {
                                requestStorageLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                            }
                        }
                    )
                    2 -> OnboardingPermissionSlide(
                        icon = Icons.Default.NotificationsActive,
                        title = "Real-Time Notifications",
                        description = "Stay updated with background transfers, sync status reports, and active cloud tasks.",
                        isGranted = hasNotifPerm,
                        buttonText = if (hasNotifPerm) "Notifications Enabled" else "Enable Notifications",
                        onGrantClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                hasNotifPerm = true
                            }
                        }
                    )
                    3 -> OnboardingPermissionSlide(
                        icon = Icons.Default.BatteryChargingFull,
                        title = "Background Sync Reliability",
                        description = "Exempt Remote Manager from aggressive battery optimization to ensure uninterrupted long file transfers.",
                        isGranted = permissionManager.grantedBatteryOptimizationExemption(),
                        buttonText = "Configure Battery Settings",
                        onGrantClick = {
                            permissionManager.requestBatteryOptimizationException()
                        }
                    )
                    4 -> OnboardingSlide(
                        iconRes = R.drawable.app_logo,
                        title = "You're All Set!",
                        subtitle = "Connect your Google Drive, OneDrive, S3, SFTP, WebDAV, Mega, or local storage remotes and start managing seamlessly.",
                        badgeText = "Ready to Explore"
                    )
                }
            }

            // Bottom Navigation Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    ) {
                        Text("Back", color = Color.White.copy(alpha = 0.7f))
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage < 4) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onFinish()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB)
                    ),
                    modifier = Modifier.height(50.dp)
                ) {
                    Text(
                        text = if (pagerState.currentPage == 4) "Get Started" else "Next",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingSlide(
    iconRes: Int,
    title: String,
    subtitle: String,
    badgeText: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E293B).copy(alpha = 0.8f),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(28.dp))
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun OnboardingPermissionSlide(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    onGrantClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B).copy(alpha = 0.7f)
        ),
        border = BorderStroke(
            1.dp,
            if (isGranted) Color(0xFF10B981).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                shape = CircleShape,
                color = if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF2563EB).copy(alpha = 0.15f)
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF10B981) else Color(0xFF38BDF8),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onGrantClick,
                enabled = !isGranted,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) Color(0xFF10B981) else Color(0xFF2563EB),
                    disabledContainerColor = Color(0xFF10B981).copy(alpha = 0.3f),
                    disabledContentColor = Color(0xFF10B981)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}