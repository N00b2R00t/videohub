package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DownloadItem
import com.example.data.DownloadRepository
import com.example.data.VideoHubDatabase
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.VideoHubViewModel
import com.example.viewmodel.VideoHubViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = VideoHubDatabase.getDatabase(applicationContext)
        val repository = DownloadRepository(database.downloadDao())

        setContent {
            MyApplicationTheme {
                val viewModel: VideoHubViewModel by viewModels {
                    VideoHubViewModelFactory(repository)
                }
                VideoHubApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoHubApp(viewModel: VideoHubViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Observe snackbar/toast events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.uiEventMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Active media player item
    var activePlayingItem by remember { mutableStateOf<DownloadItem?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            VideoHubHeader(
                searchQuery = searchQuery,
                onSearchChanged = { viewModel.updateSearchQuery(it) },
                onSearchTriggered = {
                    viewModel.searchOrAskAi(it)
                    viewModel.selectBottomTab("Explore")
                }
            )
        },
        bottomBar = {
            VideoHubBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { viewModel.selectBottomTab(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                "Home" -> HomeScreen(viewModel)
                "Explore" -> ExploreScreen(viewModel)
                "Files" -> FilesScreen(
                    viewModel = viewModel,
                    onPlayItem = { activePlayingItem = it }
                )
                "Me" -> MeScreen(viewModel)
            }
        }
    }

    // Interactive Media Player Dialog
    if (activePlayingItem != null) {
        MediaPlayerDialog(
            item = activePlayingItem!!,
            onDismiss = { activePlayingItem = null }
        )
    }
}

// Custom AppBar Search Header
@Composable
fun VideoHubHeader(
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    onSearchTriggered: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Main Search Container
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search or paste URL...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChanged,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_input")
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchChanged("") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                } else {
                    // Simulated Mic button to match design HTML exactly
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .padding(end = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                // Search resolution trigger button
                IconButton(
                    onClick = { onSearchTriggered(searchQuery) },
                    modifier = Modifier
                        .size(30.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("ask_ai_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // High polish User Avatar "JD" from mockup
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "JD",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Bottom Navigation with Material 3 active pills
@Composable
fun VideoHubBottomNavigation(
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .border(width = 0.5.dp, color = Color(0xFFE1E2E9))
    ) {
        val items = listOf(
            NavigationTabItem("Home", Icons.Filled.Home, Icons.Outlined.Home, "tab_home"),
            NavigationTabItem("Explore", Icons.Filled.Explore, Icons.Outlined.Explore, "tab_explore"),
            NavigationTabItem("Files", Icons.Filled.DownloadDone, Icons.Outlined.Download, "tab_files"),
            NavigationTabItem("Me", Icons.Filled.Person, Icons.Outlined.Person, "tab_me")
        )

        items.forEach { item ->
            val isSelected = selectedTab == item.title
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(item.title) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.title
                    )
                },
                label = { 
                    Text(
                        text = item.title, 
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ) 
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF001D36),
                    selectedTextColor = Color(0xFF001D36),
                    unselectedIconColor = Color(0xFF44474E),
                    unselectedTextColor = Color(0xFF44474E),
                    indicatorColor = Color(0xFFD3E4FF)
                ),
                modifier = Modifier.testTag(item.testTag)
            )
        }
    }
}

data class NavigationTabItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

// HOME SCREEN COMPOSABLE
@Composable
fun HomeScreen(viewModel: VideoHubViewModel) {
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val filteredMediaItems by viewModel.filteredMediaItems.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Categories Tabs Header
        CategoryTabs(
            selectedCategory = selectedCategory,
            onCategorySelected = { viewModel.selectCategory(it) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Access Sites Section
        Text(
            text = "Quick Access Sites",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        QuickAccessGrid(onSiteClick = { siteQuery ->
            val simulatedUrl = "https://www.${siteQuery.lowercase()}.com"
            viewModel.updateSearchQuery(simulatedUrl)
            viewModel.searchOrAskAi(simulatedUrl)
            viewModel.selectBottomTab("Explore")
        })

        Spacer(modifier = Modifier.height(28.dp))

        // Popular Downloads Heading
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Popular Downloads",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "SEE ALL",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    viewModel.updateSearchQuery("")
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Downloadable media list cards
        if (filteredMediaItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching items found. Try typing or pasting a URL above!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                filteredMediaItems.forEach { item ->
                    MediaDownloadCard(
                        item = item,
                        onDownloadClick = { viewModel.startDownload(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryTabs(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val categories = listOf("Featured", "Music", "Video", "Social")
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
        ) {
            categories.forEach { category ->
                val isSelected = selectedCategory == category
                val testTag = "category_${category.lowercase()}"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCategorySelected(category) }
                        .padding(vertical = 12.dp)
                        .testTag(testTag),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = category,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(3.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                    RoundedCornerShape(1.5.dp)
                                )
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}

// Geometric sites layout
@Composable
fun QuickAccessGrid(onSiteClick: (String) -> Unit) {
    val sites = listOf(
        QuickSite("YouTube", Icons.Default.PlayCircle, Color(0xFFE50914)),
        QuickSite("Instagram", Icons.Default.CameraAlt, Color(0xFFE1306C)),
        QuickSite("DailyMotion", Icons.Default.CloudDownload, Color(0xFF0077B5)),
        QuickSite("SoundCloud", Icons.Default.MusicNote, Color(0xFF333333)),
        QuickSite("TikTok", Icons.Default.VideoLibrary, Color(0xFF010101)),
        QuickSite("Vimeo", Icons.Default.VideoCall, Color(0xFF1AB7EA)),
        QuickSite("Pixabay", Icons.Default.PhotoLibrary, Color(0xFF00B22D)),
        QuickSite("Jamendo", Icons.Default.LibraryMusic, Color(0xFF7A24C4))
    )

    // Beautiful grid layout matching the geometric alignment from mockup
    val rows = sites.chunked(4)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                row.forEach { site ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onSiteClick(site.name) }
                            .width(76.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(site.color, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .shadow(2.dp, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = site.icon,
                                contentDescription = site.name,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = site.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

data class QuickSite(val name: String, val icon: ImageVector, val color: Color)

// Cards displaying downloadable content
@Composable
fun MediaDownloadCard(
    item: DownloadItem,
    onDownloadClick: () -> Unit
) {
    // Determine custom colors for container based on category
    val (containerColor, onContainerColor) = when (item.category) {
        "Music" -> Pair(Color(0xFFFFDAD6), Color(0xFF410002))
        "Video" -> Pair(Color(0xFFD1E4FF), Color(0xFF001D36))
        "Social" -> Pair(Color(0xFFE8DEF8), Color(0xFF1D192B))
        else -> Pair(Color(0xFFE8F5E9), Color(0xFF1B5E20))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail Mock (styled exactly like HTML)
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (item.isAudioOnly) Icons.Default.MusicVideo else Icons.Default.Movie,
                        contentDescription = null,
                        tint = onContainerColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.duration,
                        color = onContainerColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details (with beautiful layout from HTML)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                val subtitleText = if (item.isAudioOnly) "Audio" else "HD"
                Text(
                    text = "$subtitleText • ${item.size} • 1.2M dls",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Download Action Button (Tailwind styled bg-[#D3E4FF] text-[#001D36])
            IconButton(
                onClick = onDownloadClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFD3E4FF), CircleShape)
                    .testTag("download_button_${item.title.replace(" ", "_")}")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = Color(0xFF001D36),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// EXPLORE / SMART GEMINI ASSISTANT SCREEN
@Composable
fun ExploreScreen(viewModel: VideoHubViewModel) {
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    val suggestions = listOf(
        "Lofi Focus Beats",
        "https://youtube.com/watch?v=chill",
        "Epic Synthwave Mix",
        "Ocean Waves Relaxation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Search & Discover Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Universal Media Explorer",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = "Paste any media links (YouTube, SoundCloud, TikTok etc.) or enter video/music keywords to grab download sources instantly.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Text input for Custom Search request
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            label = { Text("Search music, video, or paste URL...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                IconButton(
                    onClick = { viewModel.searchOrAskAi(searchQuery) },
                    enabled = searchQuery.isNotBlank() && !isSearching
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Search",
                        tint = if (searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Preset quick suggestions
        Text(
            text = "Trending Searches",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { suggestion ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clickable {
                        viewModel.updateSearchQuery(suggestion)
                        viewModel.searchOrAskAi(suggestion)
                    }
                ) {
                    Text(
                        text = suggestion,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Search Results Panel
        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Crawling resources and resolving media links...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (searchResults.isNotEmpty()) {
            Text(
                text = "Grab Discovered Streams",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                searchResults.forEach { item ->
                    MediaDownloadCard(
                        item = item,
                        onDownloadClick = { viewModel.startDownload(item) }
                    )
                }
            }
        } else if (searchQuery.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No results found for \"$searchQuery\". Try searching another term or paste a valid link!",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// FILES SCREEN (ACTIVE & COMPLETED DOWNLOADS FROM ROOM)
@Composable
fun FilesScreen(
    viewModel: VideoHubViewModel,
    onPlayItem: (DownloadItem) -> Unit
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var selectedSubTab by remember { mutableStateOf("Downloading") }

    val downloadingItems = downloads.filter { it.status == "DOWNLOADING" || it.status == "PAUSED" }
    val completedItems = downloads.filter { it.status == "COMPLETED" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Heading
        Text(
            text = "My Downloaded Files",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Files sub-tabs (High Polish M3 Segmented Control)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            val subTabs = listOf("Downloading", "Completed")
            subTabs.forEach { tab ->
                val isSelected = selectedSubTab == tab
                val count = if (tab == "Downloading") downloadingItems.size else completedItems.size
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedSubTab = tab }
                        .background(
                            if (isSelected) Color.White
                            else Color.Transparent
                        )
                        .padding(vertical = 10.dp)
                        .shadow(if (isSelected) 1.dp else 0.dp, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tab,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        if (count > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = count.toString(),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val currentList = if (selectedSubTab == "Downloading") downloadingItems else completedItems

        // Empty state support (mandated by design guidelines!)
        if (currentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = if (selectedSubTab == "Downloading") Icons.Outlined.CloudDownload else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (selectedSubTab == "Downloading") "No files actively downloading." else "No completed downloads yet.",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (selectedSubTab == "Downloading") "Tap download on any popular songs/videos or paste URLs on the Home tab!" else "Download some high quality media files to store locally on your database.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(currentList, key = { it.id }) { item ->
                    FileDownloadItemRow(
                        item = item,
                        onTogglePauseResume = { viewModel.togglePauseResumeDownload(item) },
                        onDelete = { viewModel.deleteDownload(item) },
                        onPlay = { onPlayItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun FileDownloadItemRow(
    item: DownloadItem,
    onTogglePauseResume: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit
) {
    val (containerColor, onContainerColor) = when (item.category) {
        "Music" -> Pair(Color(0xFFFFDAD6), Color(0xFF410002))
        "Video" -> Pair(Color(0xFFD1E4FF), Color(0xFF001D36))
        else -> Pair(Color(0xFFE8DEF8), Color(0xFF1D192B))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Category small thumb icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(containerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isAudioOnly) Icons.Default.MusicNote else Icons.Default.Movie,
                        contentDescription = null,
                        tint = onContainerColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.category} • ${item.size}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Delete Download
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp).testTag("delete_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (item.status != "COMPLETED") {
                // Downloading/Progress visual bar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape),
                        color = if (item.status == "PAUSED") Color.Gray else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${(item.progress * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Pause/Resume Button
                    IconButton(
                        onClick = onTogglePauseResume,
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (item.status == "DOWNLOADING") Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (item.status == "DOWNLOADING") "Pause" else "Resume",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (item.status == "PAUSED") "Paused" else "Downloading...",
                        fontSize = 10.sp,
                        color = if (item.status == "PAUSED") Color.Gray else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (item.status == "PAUSED") "0 KB/s" else "3.2 MB/s",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // Play Button for Completed items (addresses dead-end UI affordance rules!)
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("play_button_${item.id}"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play / Open File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// PROFILE & SETTINGS TAB
@Composable
fun MeScreen(viewModel: VideoHubViewModel) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val completedCount = downloads.count { it.status == "COMPLETED" }

    val safeSearchEnabled by viewModel.safeSearchEnabled.collectAsStateWithLifecycle()
    val fastDownloadMode by viewModel.fastDownloadMode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // User Profile Card
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Avatar JD
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "JD",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Jane Doe",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "VideoHub Explorer Pro",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Stat widgets
        Text(
            text = "Storage & Stats",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Downloaded",
                value = "$completedCount Files",
                icon = Icons.Default.FileDownload,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Total Storage",
                value = "${(completedCount * 14.3).coerceAtLeast(0.0).let { "%.1f".format(it) }} MB",
                icon = Icons.Default.Storage,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Settings Blocks
        Text(
            text = "Preferences",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                PreferenceToggleRow(
                    title = "Safe Search filter",
                    description = "Block adult or hazardous downloads",
                    checked = safeSearchEnabled,
                    onCheckedChange = { viewModel.safeSearchEnabled.value = it }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PreferenceToggleRow(
                    title = "Parallel high-speed downloads",
                    description = "Accelerates multiple task speeds",
                    checked = fastDownloadMode,
                    onCheckedChange = { viewModel.fastDownloadMode.value = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Actions Block
        Text(
            text = "Database Management",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.clearAllDownloads() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFDAD6), contentColor = Color(0xFF410002))
        ) {
            Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear All Download Records", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(28.dp))

        // About Block
        Text(
            text = "About VidMate VideoHub",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = "VideoHub crawls free-to-use streams from publicly indexed sites. Downloading copyright material without authorization is strictly prohibited.",
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun PreferenceToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(text = description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
        )
    }
}

// INTERACTIVE MEDIA PLAYER DIALOG
@Composable
fun MediaPlayerDialog(
    item: DownloadItem,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        var isPlaying by remember { mutableStateOf(true) }
        var playProgress by remember { mutableFloatStateOf(0.0f) }

        // Simulated playback progress
        LaunchedEffect(isPlaying) {
            if (isPlaying) {
                while (playProgress < 1.0f) {
                    delay(300L)
                    playProgress = (playProgress + 0.015f).coerceAtMost(1.0f)
                }
                isPlaying = false
            }
        }

        // Beautiful infinite rotation animation for CD Disc
        val infiniteTransition = rememberInfiniteTransition(label = "CD Rotation")
        val rotationAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "Rotation"
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Title
                Text(
                    text = "Now Playing",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Beautiful record disc / music waves canvas
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF111318)),
                    contentAlignment = Alignment.Center
                ) {
                    // Simulated rotating CD disc
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .rotate(if (isPlaying) rotationAngle else 0f)
                            .border(3.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                            .padding(12.dp)
                            .background(Color.Black, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // Vinyl lines
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, Color.DarkGray, CircleShape)
                                .padding(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, Color.DarkGray, CircleShape)
                                    .padding(16.dp)
                            ) {
                                // Album artwork center
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    MaterialTheme.colorScheme.secondaryContainer
                                                )
                                            ),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.isAudioOnly) Icons.Default.MusicNote else Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Slider tracking progress
                Slider(
                    value = playProgress,
                    onValueChange = { playProgress = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Helper to get play seconds string
                    val totalSec = 225 // 3:45 length
                    val currentSec = (playProgress * totalSec).toInt()
                    val curMin = currentSec / 60
                    val curSec = currentSec % 60
                    Text(
                        text = "$curMin:${curSec.toString().padStart(2, '0')}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.duration,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Player Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { playProgress = (playProgress - 0.1f).coerceAtLeast(0f) }) {
                        Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Rewind", modifier = Modifier.size(28.dp))
                    }
                    IconButton(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = { playProgress = (playProgress + 0.1f).coerceAtMost(1f) }) {
                        Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Forward", modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Close button
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "STOP PLAYBACK", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
