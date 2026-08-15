package ca.pkay.rcloneexplorer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.pkay.rcloneexplorer.Dialogs.SortDialog

enum class SortField(val title: String) {
    NAME("Name"),
    SIZE("Size"),
    DATE("Date Modified")
}

enum class SortDirection(val title: String) {
    ASCENDING("Ascending (A-Z, Smallest, Oldest)"),
    DESCENDING("Descending (Z-A, Largest, Newest)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    currentSortOrder: Int,
    onDismiss: () -> Unit,
    onApplySort: (Int) -> Unit
) {
    var selectedField by remember(currentSortOrder) {
        mutableStateOf(
            when (currentSortOrder) {
                SortDialog.SIZE_ASCENDING, SortDialog.SIZE_DESCENDING -> SortField.SIZE
                SortDialog.MOD_TIME_ASCENDING, SortDialog.MOD_TIME_DESCENDING -> SortField.DATE
                else -> SortField.NAME
            }
        )
    }

    var selectedDirection by remember(currentSortOrder) {
        mutableStateOf(
            when (currentSortOrder) {
                SortDialog.ALPHA_DESCENDING, SortDialog.SIZE_DESCENDING, SortDialog.MOD_TIME_DESCENDING -> SortDirection.DESCENDING
                else -> SortDirection.ASCENDING
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Sort Files",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sort By Field
            Text(
                text = "Sort By",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            SortField.values().forEach { field ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedField = field }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedField == field,
                        onClick = { selectedField = field }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = field.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Sort Direction
            Text(
                text = "Order",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            SortDirection.values().forEach { direction ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedDirection = direction }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedDirection == direction,
                        onClick = { selectedDirection = direction }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = direction.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val newSortOrder = when (selectedField) {
                            SortField.NAME -> if (selectedDirection == SortDirection.ASCENDING) SortDialog.ALPHA_ASCENDING else SortDialog.ALPHA_DESCENDING
                            SortField.SIZE -> if (selectedDirection == SortDirection.ASCENDING) SortDialog.SIZE_ASCENDING else SortDialog.SIZE_DESCENDING
                            SortField.DATE -> if (selectedDirection == SortDirection.ASCENDING) SortDialog.MOD_TIME_ASCENDING else SortDialog.MOD_TIME_DESCENDING
                        }
                        onApplySort(newSortOrder)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Apply")
                }
            }
        }
    }
}
