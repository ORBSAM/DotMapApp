package com.example.dotmapapp

import androidx.compose.material.icons.filled.List
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private val vm: DotViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // call before super.onCreate / setContent
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MapDotsScreen(vm) } }
    }
}

/**
 * toComposeColor
 *
 * The colors, for the dots
 */
fun DotColor.toComposeColor() = when (this) {
    DotColor.RED -> Color(0xFFD32F2F)
    DotColor.YELLOW -> Color(0xFFFBC02D)
    DotColor.ORANGE -> Color(0xFFF57C00)
}

/**
 * toMarkerHue
 *
 */
fun DotColor.toMarkerHue() = when (this) {
    DotColor.RED -> BitmapDescriptorFactory.HUE_RED
    DotColor.YELLOW -> BitmapDescriptorFactory.HUE_YELLOW
    DotColor.ORANGE -> BitmapDescriptorFactory.HUE_ORANGE
}

/**
 * distanceMeters
 *
 * For proximity, calculate the distance of the dot away from the user, in meters.
 */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}

@SuppressLint("MissingPermission", "UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
* MapDotsScreen
 *
*/
fun MapDotsScreen(vm: DotViewModel) {
    val ctx = LocalContext.current
    val dots by vm.dots.collectAsState()
    val filters by vm.activeFilters.collectAsState()
    val visible = dots.filter { it.color in filters }

    var hasPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPerm = it }
    LaunchedEffect(Unit) { if (!hasPerm) permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    var myLocation by remember { mutableStateOf<LatLng?>(null) }
    val fused = remember { LocationServices.getFusedLocationProviderClient(ctx) }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(39.8, -98.6), 4f)
    }
    LaunchedEffect(hasPerm) {
        if (hasPerm) fused.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                myLocation = LatLng(it.latitude, it.longitude)
                camera.position = CameraPosition.fromLatLngZoom(myLocation!!, 15f)
            }
        }
    }

    // dialog state for adding a new dot
    var pendingLatLng by remember { mutableStateOf<LatLng?>(null) }
    // popup state for viewing an existing dot
    var selectedDot by remember { mutableStateOf<Dot?>(null) }
    // bottom sheet state for the nearby list
    var showList by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map Dots") },
                actions = {
                    IconButton(onClick = { showList = true }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Nearby dots")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // --- filter row ---
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DotColor.entries.forEach { color ->
                    FilterChip(
                        selected = color in filters,
                        onClick = { vm.toggleFilter(color) },
                        label = { Text(color.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        leadingIcon = {
                            Box(
                                Modifier.size(12.dp)
                                    .background(color.toComposeColor(), shape = CircleShape)
                            )
                        }
                    )
                }
            }

            // --- map ---
            GoogleMap(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                cameraPositionState = camera,
                properties = MapProperties(isMyLocationEnabled = hasPerm),
                onMapClick = { latLng -> pendingLatLng = latLng }
            ) {
                visible.forEach { dot ->
                    Marker(
                        state = MarkerState(LatLng(dot.lat, dot.lng)),
                        icon = BitmapDescriptorFactory.defaultMarker(dot.color.toMarkerHue()),
                        onClick = { selectedDot = dot; true }
                    )
                }
            }
        }
    }

    // --- add-dot dialog ---
    pendingLatLng?.let { latLng ->
        AddDotDialog(
            onDismiss = { pendingLatLng = null },
            onConfirm = { desc, color ->
                vm.addDot(latLng.latitude, latLng.longitude, desc, color)
                pendingLatLng = null
            }
        )
    }

    // --- view-dot popup ---
    selectedDot?.let { dot ->
        AlertDialog(
            onDismissRequest = { selectedDot = null },
            title = { Text(dot.color.name.lowercase().replaceFirstChar { it.uppercase() } + " dot") },
            text = { Text(dot.description) },
            confirmButton = {
                TextButton(onClick = { selectedDot = null }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = { vm.deleteDot(dot.id); selectedDot = null }) { Text("Delete") }
            }
        )
    }

    // --- nearby list bottom sheet ---
    if (showList) {
        ModalBottomSheet(onDismissRequest = { showList = false }) {
            val sorted = remember(visible, myLocation) {
                myLocation?.let { me ->
                    visible.sortedBy { distanceMeters(me.latitude, me.longitude, it.lat, it.lng) }
                } ?: visible
            }
            LazyColumn(Modifier.padding(16.dp)) {
                items(sorted) { dot ->
                    val distText = myLocation?.let {
                        "%.0f m".format(distanceMeters(it.latitude, it.longitude, dot.lat, dot.lng))
                    } ?: ""
                    ListItem(
                        headlineContent = { Text(dot.description) },
                        supportingContent = { Text(distText) },
                        leadingContent = {
                            Box(Modifier.size(16.dp).background(dot.color.toComposeColor(),
                                CircleShape
                            ))
                        },
                        modifier = Modifier.clickable {
                            showList = false
                            camera.position = CameraPosition.fromLatLngZoom(LatLng(dot.lat, dot.lng), 17f)
                        }
                    )
                }
            }
        }
    }
}

/**
 * AddDotDialog
 *
 *  When you tap, you are asked to add a dot.
 *  A few options: colors, and then a description.
 *
 *  @param onDismiss - nothing is changed if dismissed
 *  @param onConfirm - if confirmed, the dot color and description are stored
 */
@Composable
fun AddDotDialog(onDismiss: () -> Unit, onConfirm: (String, DotColor) -> Unit) {
    // default description is empty
    var desc by remember { mutableStateOf("") }
    // default dot color is red
    var color by remember { mutableStateOf(DotColor.RED) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a dot") },
        text = {
            Column {
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Description") }
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DotColor.entries.forEach { c ->
                        FilterChip(
                            selected = color == c,
                            onClick = { color = c },
                            label = { Text(c.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (desc.isNotBlank()) onConfirm(desc, color) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}