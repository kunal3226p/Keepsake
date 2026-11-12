package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.PhotoEntity

private val RELATIONSHIPS = listOf(
    "Mother", "Father", "Partner", "Daughter", "Son", "Grandparent", "Sibling", "Friend"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FaceCropperDialog(
    initialPhotoUri: Uri?,
    recentLibraryPhotos: List<PhotoEntity>,
    onDismiss: () -> Unit,
    onConfirmFace: (
        name: String,
        relationship: String,
        photoUri: Uri,
        centerXFraction: Float,
        centerYFraction: Float,
        cropSizeFraction: Float
    ) -> Unit
) {
    var selectedUri by remember { mutableStateOf(initialPhotoUri) }
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Family") }
    var centerX by remember { mutableFloatStateOf(0.5f) }
    var centerY by remember { mutableFloatStateOf(0.4f) }
    var cropFraction by remember { mutableFloatStateOf(0.38f) }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            centerX = 0.5f
            centerY = 0.4f
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Family Face",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close face selector"
                        )
                    }
                }

                Text(
                    text = "Choose a clear photo and drag the circle directly over their face so AI can identify and tag them across your entire collection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            pickPhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_reference_face_photo")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick from Device")
                    }
                }

                if (recentLibraryPhotos.isNotEmpty()) {
                    Text(
                        text = "Or pick from your Keepsake library:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentLibraryPhotos.take(20), key = { it.id }) { item ->
                            val itemUri = remember(item.uriString) { Uri.parse(item.uriString) }
                            val isSelected = selectedUri?.toString() == item.uriString
                            AsyncImage(
                                model = itemUri,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedUri = itemUri
                                        centerX = 0.5f
                                        centerY = 0.4f
                                    }
                            )
                        }
                    }
                }

                if (selectedUri != null) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .pointerInput(selectedUri) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    centerX = (centerX + dragAmount.x / w).coerceIn(0.1f, 0.9f)
                                    centerY = (centerY + dragAmount.y / h).coerceIn(0.1f, 0.9f)
                                }
                            }
                    ) {
                        AsyncImage(
                            model = selectedUri,
                            contentDescription = "Selected photo for face cropping",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        val reticleColor = MaterialTheme.colorScheme.primaryContainer
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val minDim = minOf(size.width, size.height)
                            val radius = (minDim * cropFraction) / 2f
                            val cx = size.width * centerX
                            val cy = size.height * centerY

                            val overlayPath = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                                addOval(
                                    androidx.compose.ui.geometry.Rect(
                                        cx - radius,
                                        cy - radius,
                                        cx + radius,
                                        cy + radius
                                    )
                                )
                            }
                            drawPath(overlayPath, color = Color(0x881E1915))
                            drawCircle(
                                color = reticleColor,
                                radius = radius,
                                center = Offset(cx, cy),
                                style = Stroke(width = 6f)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Face Circle Size",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = cropFraction,
                            onValueChange = { cropFraction = it },
                            valueRange = 0.22f..0.75f
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Family Member Name") },
                    placeholder = { Text("e.g., Mom, Maya, Grandpa Leo") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("family_member_name_input")
                )

                Text(
                    text = "Relationship",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RELATIONSHIPS.forEach { role ->
                        FilterChip(
                            selected = relationship == role,
                            onClick = { relationship = role },
                            label = { Text(role) }
                        )
                    }
                }

                Button(
                    onClick = {
                        val uri = selectedUri ?: return@Button
                        if (name.isNotBlank()) {
                            onConfirmFace(
                                name.trim(),
                                relationship,
                                uri,
                                centerX,
                                centerY,
                                cropFraction
                            )
                        }
                    },
                    enabled = selectedUri != null && name.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_family_face_button")
                ) {
                    Text("Save Face & Scan Collection")
                }
            }
        }
    }
}
