package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.configureAppCheck
import com.example.data.ai.KeepsakeAiService
import com.example.data.firestore.KeepsakeRepository
import com.example.data.local.KeepsakeDatabase
import com.example.data.local.PhotoEntity
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOut
import com.example.ui.components.FaceCropperDialog
import com.example.ui.components.PhotoDetailDialog
import com.example.ui.screens.FamilyFacesScreen
import com.example.ui.screens.LibraryAlbumsScreen
import com.example.ui.screens.MonthlyCollagesScreen
import com.example.ui.theme.KeepsakeTheme
import com.example.ui.viewmodel.KeepsakeViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_ALBUMS = "albums"
const val ROUTE_FAMILY = "family"
const val ROUTE_COLLAGES = "collages"

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val DESTINATIONS = listOf(
    NavDestinationItem(
        route = ROUTE_ALBUMS,
        label = "Albums",
        selectedIcon = Icons.Rounded.Collections,
        unselectedIcon = Icons.Outlined.Collections
    ),
    NavDestinationItem(
        route = ROUTE_FAMILY,
        label = "Family",
        selectedIcon = Icons.Rounded.Face,
        unselectedIcon = Icons.Outlined.Face
    ),
    NavDestinationItem(
        route = ROUTE_COLLAGES,
        label = "Collages",
        selectedIcon = Icons.Rounded.AutoAwesomeMosaic,
        unselectedIcon = Icons.Outlined.AutoAwesomeMosaic
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            KeepsakeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation() {
    val auth = Firebase.auth
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            KeepsakeMainShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: user.email ?: "Family Archive",
                onSignOutClick = {
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepsakeMainShell(
    currentUserId: String,
    userDisplayName: String,
    onSignOutClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: KeepsakeViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val dao = KeepsakeDatabase.getInstance(app).photoDao()
                KeepsakeViewModel(
                    userId = currentUserId,
                    repository = KeepsakeRepository(db),
                    photoDao = dao,
                    aiService = KeepsakeAiService()
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_ALBUMS

    val photos by viewModel.filteredPhotos.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalPhotoCount.collectAsStateWithLifecycle()
    val analyzedCount by viewModel.analyzedPhotoCount.collectAsStateWithLifecycle()
    val favoriteCount by viewModel.favoritePhotoCount.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val albumCounts by viewModel.albumCounts.collectAsStateWithLifecycle()
    val familyMembers by viewModel.familyMembers.collectAsStateWithLifecycle()
    val collages by viewModel.collages.collectAsStateWithLifecycle()
    val monthSummaries by viewModel.monthSummaries.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val recentPhotos by viewModel.allPhotosForPicker.collectAsStateWithLifecycle()

    var selectedPhotoForDetail by remember { mutableStateOf<PhotoEntity?>(null) }
    var showFaceCropper by remember { mutableStateOf(false) }
    var faceCropperInitialUri by remember { mutableStateOf<Uri?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissStatus()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.importFolder(context.applicationContext, treeUri)
        }
    }

    val multiPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importSelectedPhotos(context.applicationContext, uris)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Keepsake",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    actions = {
                        IconButton(
                            onClick = onSignOutClick,
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Sign out ($userDisplayName)"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        DESTINATIONS.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(ROUTE_ALBUMS) { saveState = true }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail {
                        DESTINATIONS.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(ROUTE_ALBUMS) { saveState = true }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_ALBUMS
                    ) {
                        composable(ROUTE_ALBUMS) {
                            LibraryAlbumsScreen(
                                photos = photos,
                                totalCount = totalCount,
                                analyzedCount = analyzedCount,
                                favoriteCount = favoriteCount,
                                albums = albums,
                                albumCounts = albumCounts,
                                filterState = filterState,
                                processingState = processingState,
                                onSearchChange = viewModel::updateSearchQuery,
                                onSelectAlbum = viewModel::selectAlbumFilter,
                                onToggleFavorites = viewModel::toggleFavoritesOnly,
                                onClearFilters = viewModel::clearAllFilters,
                                onImportFolderClick = { folderPickerLauncher.launch(null) },
                                onPickPhotosClick = {
                                    multiPhotoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onResumeAiClick = {
                                    viewModel.startAiCategorization(context.applicationContext)
                                },
                                onPauseAiClick = viewModel::pauseActiveAiTask,
                                onPhotoClick = { selectedPhotoForDetail = it },
                                onFavoriteClick = viewModel::toggleFavorite
                            )
                        }

                        composable(ROUTE_FAMILY) {
                            FamilyFacesScreen(
                                familyMembers = familyMembers,
                                totalPhotoCount = totalCount,
                                processingState = processingState,
                                onAddFamilyFaceClick = {
                                    faceCropperInitialUri = null
                                    showFaceCropper = true
                                },
                                onScanCollectionClick = {
                                    viewModel.scanCollectionForFamilyFaces(
                                        context.applicationContext,
                                        rescanAll = true
                                    )
                                },
                                onViewMemberPhotos = { member ->
                                    viewModel.selectMemberFilter(member.id, member.name)
                                    navController.navigate(ROUTE_ALBUMS) {
                                        launchSingleTop = true
                                    }
                                },
                                onDeleteMember = { member ->
                                    viewModel.deleteFamilyMember(member.id)
                                }
                            )
                        }

                        composable(ROUTE_COLLAGES) {
                            MonthlyCollagesScreen(
                                monthSummaries = monthSummaries,
                                collages = collages,
                                processingState = processingState,
                                onCraftCollage = { monthKey, monthLabel ->
                                    viewModel.craftMonthlyCollage(
                                        context.applicationContext,
                                        monthKey,
                                        monthLabel
                                    )
                                },
                                onUpdateCollage = viewModel::updateCollageAppearance,
                                onSaveCollageToGallery = { collage ->
                                    viewModel.saveCollageToGallery(context.applicationContext, collage)
                                },
                                onShareCollage = { collage ->
                                    viewModel.shareCollage(context, collage)
                                },
                                onBrowseMonthPhotos = { monthKey, monthLabel ->
                                    viewModel.selectMonthFilter(monthKey, monthLabel)
                                    navController.navigate(ROUTE_ALBUMS) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    val activeDetail = selectedPhotoForDetail
    if (activeDetail != null) {
        PhotoDetailDialog(
            photo = activeDetail,
            onDismiss = { selectedPhotoForDetail = null },
            onToggleFavorite = { p ->
                viewModel.toggleFavorite(p)
                selectedPhotoForDetail = p.copy(isFavorite = !p.isFavorite)
            },
            onUpdateAlbum = { p, newAlbum ->
                viewModel.updatePhotoAlbum(p, newAlbum)
                selectedPhotoForDetail = p.copy(albumCategory = newAlbum)
            },
            onSelectFaceFromPhoto = { uri ->
                selectedPhotoForDetail = null
                faceCropperInitialUri = uri
                showFaceCropper = true
            }
        )
    }

    if (showFaceCropper) {
        FaceCropperDialog(
            initialPhotoUri = faceCropperInitialUri,
            recentLibraryPhotos = recentPhotos,
            onDismiss = { showFaceCropper = false },
            onConfirmFace = { name, relationship, uri, cx, cy, sizeFrac ->
                showFaceCropper = false
                viewModel.addFamilyMemberFromFaceCrop(
                    appContext = context.applicationContext,
                    name = name,
                    relationship = relationship,
                    referencePhotoUri = uri,
                    centerXFraction = cx,
                    centerYFraction = cy,
                    cropSizeFraction = sizeFrac
                )
            }
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Notice") },
            text = { Text(errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text("OK")
                }
            }
        )
    }
}
