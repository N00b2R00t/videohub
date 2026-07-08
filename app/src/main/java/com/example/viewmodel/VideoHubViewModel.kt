package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.data.DownloadItem
import com.example.data.DownloadRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

class VideoHubViewModel(private val repository: DownloadRepository) : ViewModel() {

    // Selected Bottom Tab: "Home", "Explore", "Files", "Me"
    private val _selectedTab = MutableStateFlow("Home")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    // Selected Category Tab in Home: "Featured", "Music", "Video", "Social"
    private val _selectedCategory = MutableStateFlow("Featured")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Search query in AppBar
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Search Results state
    private val _searchResults = MutableStateFlow<List<DownloadItem>>(emptyList())
    val searchResults: StateFlow<List<DownloadItem>> = _searchResults.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // Observe real downloads from Room Database
    val downloads: StateFlow<List<DownloadItem>> = repository.allDownloads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Predefined available items in the VideoHub
    private val _staticMediaItems = MutableStateFlow(getInitialMediaItems())
    val staticMediaItems: StateFlow<List<DownloadItem>> = _staticMediaItems.asStateFlow()

    // Combined or searched media list based on filters & queries
    val filteredMediaItems: StateFlow<List<DownloadItem>> = combine(
        _staticMediaItems,
        _selectedCategory,
        _searchQuery
    ) { items, category, query ->
        items.filter { item ->
            // Filter by category
            val matchesCategory = if (category == "Featured") {
                item.category == "Featured" || item.category == "Video"
            } else {
                item.category.equals(category, ignoreCase = true)
            }
            // Filter by query
            val matchesQuery = query.isEmpty() || 
                    item.title.contains(query, ignoreCase = true) || 
                    item.url.contains(query, ignoreCase = true)
            
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state messaging (snackbars)
    private val _uiEventMessage = MutableSharedFlow<String>()
    val uiEventMessage: SharedFlow<String> = _uiEventMessage.asSharedFlow()

    // Settings States
    val safeSearchEnabled = MutableStateFlow(true)
    val fastDownloadMode = MutableStateFlow(true)

    fun selectBottomTab(tab: String) {
        _selectedTab.value = tab
        if (tab != "Home") {
            // Clear AI response/query when moving away if wanted, or keep it
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Direct download action for any item
    fun startDownload(item: DownloadItem) {
        viewModelScope.launch {
            // Check if already downloading or completed
            val existingList = downloads.value
            val isAlreadyDownloading = existingList.any { 
                it.title == item.title && (it.status == "DOWNLOADING" || it.status == "COMPLETED") 
            }
            if (isAlreadyDownloading) {
                _uiEventMessage.emit("Already in downloads: ${item.title}")
                return@launch
            }

            // Insert new downloading item to DB
            val newItem = item.copy(
                id = 0, // Reset id to auto generate
                status = "DOWNLOADING",
                progress = 0.0f,
                timestamp = System.currentTimeMillis()
            )
            val insertedId = repository.insertDownload(newItem)
            _uiEventMessage.emit("Starting download: ${item.title}")

            // Start simulated progress background task
            simulateDownloadProgress(insertedId.toInt())
        }
    }

    // Simulate progress updates in DB
    private fun simulateDownloadProgress(itemId: Int) {
        viewModelScope.launch {
            var currentProgress = 0.0f
            while (currentProgress < 1.0f) {
                delay(if (fastDownloadMode.value) 400L else 800L)
                val currentItem = repository.getDownloadById(itemId) ?: break
                
                // If paused or deleted, cancel simulation
                if (currentItem.status == "PAUSED" || currentItem.status == "FAILED") {
                    break
                }

                val increment = Random.nextFloat() * 0.25f + 0.10f
                currentProgress = (currentProgress + increment).coerceAtMost(1.0f)

                val updatedItem = currentItem.copy(
                    progress = currentProgress,
                    status = if (currentProgress >= 1.0f) "COMPLETED" else "DOWNLOADING"
                )
                repository.updateDownload(updatedItem)
            }
            
            val finalItem = repository.getDownloadById(itemId)
            if (finalItem != null && finalItem.status == "COMPLETED") {
                _uiEventMessage.emit("Completed download: ${finalItem.title}")
            }
        }
    }

    // Pause/Resume download
    fun togglePauseResumeDownload(item: DownloadItem) {
        viewModelScope.launch {
            val newStatus = if (item.status == "DOWNLOADING") "PAUSED" else "DOWNLOADING"
            val updated = item.copy(status = newStatus)
            repository.updateDownload(updated)
            _uiEventMessage.emit("${if (newStatus == "PAUSED") "Paused" else "Resumed"}: ${item.title}")
            
            if (newStatus == "DOWNLOADING") {
                simulateDownloadProgress(item.id)
            }
        }
    }

    // Delete download
    fun deleteDownload(item: DownloadItem) {
        viewModelScope.launch {
            repository.deleteDownload(item)
            _uiEventMessage.emit("Removed download: ${item.title}")
        }
    }

    // Clear all history
    fun clearAllDownloads() {
        viewModelScope.launch {
            repository.clearAllDownloads()
            _uiEventMessage.emit("Cleared all download records")
        }
    }

    // Universal search & link resolution without external AI
    fun searchOrAskAi(prompt: String) {
        if (prompt.isBlank()) return
        _searchQuery.value = prompt
        _isAiLoading.value = true
        _searchResults.value = emptyList()

        viewModelScope.launch {
            // Simulate natural crawler/resolution delay
            delay(600L)

            val query = prompt.trim()
            val results = mutableListOf<DownloadItem>()

            // 1. Check if the query is a URL
            val isUrl = query.startsWith("http://", ignoreCase = true) || 
                        query.startsWith("https://", ignoreCase = true) || 
                        query.startsWith("www.", ignoreCase = true) ||
                        query.contains(".com", ignoreCase = true) ||
                        query.contains(".org", ignoreCase = true) ||
                        query.contains(".net", ignoreCase = true)

            if (isUrl) {
                // Parse clean display name from the URL
                val domain = when {
                    query.contains("youtube.com", ignoreCase = true) || query.contains("youtu.be", ignoreCase = true) -> "YouTube"
                    query.contains("soundcloud.com", ignoreCase = true) -> "SoundCloud"
                    query.contains("instagram.com", ignoreCase = true) -> "Instagram"
                    query.contains("tiktok.com", ignoreCase = true) -> "TikTok"
                    query.contains("vimeo.com", ignoreCase = true) -> "Vimeo"
                    query.contains("pixabay.com", ignoreCase = true) -> "Pixabay"
                    query.contains("pexels.com", ignoreCase = true) -> "Pexels"
                    query.contains("jamendo.com", ignoreCase = true) -> "Jamendo"
                    else -> "Web Stream"
                }

                val titleStem = query.substringBefore("?").substringBefore("&")
                    .replace("https://", "", ignoreCase = true)
                    .replace("http://", "", ignoreCase = true)
                    .replace("www.", "", ignoreCase = true)
                    .replace(".com", "", ignoreCase = true)
                    .replace(".org", "", ignoreCase = true)
                    .replace(".net", "", ignoreCase = true)
                    .replace("/", " ")
                    .trim()

                val cleanTitle = if (titleStem.length > 30) titleStem.take(30) + "..." else titleStem

                // Add 1 Video Option
                results.add(
                    DownloadItem(
                        id = 0,
                        title = if (cleanTitle.isNotEmpty()) "$cleanTitle (Video HD)" else "$domain Video Stream",
                        url = query,
                        category = "Video",
                        size = "${Random.nextInt(15, 65)}.${Random.nextInt(0, 9)} MB",
                        progress = 0f,
                        status = "PAUSED",
                        duration = "${Random.nextInt(2, 15)}:${Random.nextInt(10, 59)}",
                        isAudioOnly = false
                    )
                )

                // Add 1 Audio Option
                results.add(
                    DownloadItem(
                        id = 0,
                        title = if (cleanTitle.isNotEmpty()) "$cleanTitle (Audio MP3)" else "$domain Audio Track",
                        url = query,
                        category = "Music",
                        size = "${Random.nextInt(3, 12)}.${Random.nextInt(0, 9)} MB",
                        progress = 0f,
                        status = "PAUSED",
                        duration = "${Random.nextInt(2, 8)}:${Random.nextInt(10, 59)}",
                        isAudioOnly = true
                    )
                )
                
                _uiEventMessage.emit("Successfully resolved media streams from $domain!")
            } else {
                // 2. Keyword/text search

                // First find matches in existing static items
                val staticMatches = _staticMediaItems.value.filter {
                    it.title.contains(query, ignoreCase = true) || 
                    it.category.contains(query, ignoreCase = true) ||
                    it.url.contains(query, ignoreCase = true)
                }
                results.addAll(staticMatches)

                // Add dynamic results matching the keyword
                val capitalizedQuery = query.replaceFirstChar { it.uppercase() }
                
                // If the user searches for music-related terms
                val isMusicRelated = query.contains("music", ignoreCase = true) || 
                                     query.contains("song", ignoreCase = true) || 
                                     query.contains("audio", ignoreCase = true) ||
                                     query.contains("lofi", ignoreCase = true) ||
                                     query.contains("mp3", ignoreCase = true) ||
                                     query.contains("beat", ignoreCase = true)

                // If the user searches for video-related terms
                val isVideoRelated = query.contains("video", ignoreCase = true) || 
                                     query.contains("movie", ignoreCase = true) || 
                                     query.contains("film", ignoreCase = true) ||
                                     query.contains("compilation", ignoreCase = true) ||
                                     query.contains("mp4", ignoreCase = true)

                if (isMusicRelated) {
                    results.add(
                        DownloadItem(
                            id = 0,
                            title = "$capitalizedQuery Radio Edit",
                            url = "https://jamendo.com/track/${query.lowercase()}",
                            category = "Music",
                            size = "${Random.nextInt(4, 9)}.${Random.nextInt(0, 9)} MB",
                            progress = 0f,
                            status = "PAUSED",
                            duration = "${Random.nextInt(3, 5)}:${Random.nextInt(10, 59)}",
                            isAudioOnly = true
                        )
                    )
                    results.add(
                        DownloadItem(
                            id = 0,
                            title = "$capitalizedQuery (Acoustic Version)",
                            url = "https://soundcloud.com/${query.lowercase()}-acoustic",
                            category = "Music",
                            size = "${Random.nextInt(3, 7)}.${Random.nextInt(0, 9)} MB",
                            progress = 0f,
                            status = "PAUSED",
                            duration = "${Random.nextInt(2, 5)}:${Random.nextInt(10, 59)}",
                            isAudioOnly = true
                        )
                    )
                } else if (isVideoRelated) {
                    results.add(
                        DownloadItem(
                            id = 0,
                            title = "$capitalizedQuery [Full HD 1080p]",
                            url = "https://youtube.com/watch?v=${query.lowercase()}",
                            category = "Video",
                            size = "${Random.nextInt(25, 80)}.${Random.nextInt(0, 9)} MB",
                            progress = 0f,
                            status = "PAUSED",
                            duration = "${Random.nextInt(5, 20)}:${Random.nextInt(10, 59)}",
                            isAudioOnly = false
                        )
                    )
                    results.add(
                        DownloadItem(
                            id = 0,
                            title = "$capitalizedQuery Cinematic B-Roll",
                            url = "https://pexels.com/video/${query.lowercase()}",
                            category = "Video",
                            size = "${Random.nextInt(15, 45)}.${Random.nextInt(0, 9)} MB",
                            progress = 0f,
                            status = "PAUSED",
                            duration = "1:30",
                            isAudioOnly = false
                        )
                    )
                } else {
                    // General query: provide a video and an audio result!
                    results.add(
                        DownloadItem(
                            id = 0,
                            title = "$capitalizedQuery Stream Mix",
                            url = "https://youtube.com/watch?v=${query.lowercase()}",
                            category = "Video",
                            size = "${Random.nextInt(20, 55)}.${Random.nextInt(0, 9)} MB",
                            progress = 0f,
                            status = "PAUSED",
                            duration = "${Random.nextInt(4, 12)}:${Random.nextInt(10, 59)}",
                            isAudioOnly = false
                        )
                    )
                    results.add(
                        DownloadItem(
                            id = 0,
                            title = "$capitalizedQuery (Official Soundtrack)",
                            url = "https://soundcloud.com/${query.lowercase()}-soundtrack",
                            category = "Music",
                            size = "${Random.nextInt(5, 12)}.${Random.nextInt(0, 9)} MB",
                            progress = 0f,
                            status = "PAUSED",
                            duration = "${Random.nextInt(3, 6)}:${Random.nextInt(10, 59)}",
                            isAudioOnly = true
                        )
                    )
                }
                _uiEventMessage.emit("Found ${results.size} matches for \"$query\"")
            }

            // Update searchResults state flow
            _searchResults.value = results
            _isAiLoading.value = false
            
            // Also, insert resolved dynamic items to staticMediaItems list if they don't already exist, 
            // so they're searchable globally inside the app!
            val currentStaticList = _staticMediaItems.value.toMutableList()
            var addedAny = false
            results.forEach { result ->
                if (!currentStaticList.any { it.title.equals(result.title, ignoreCase = true) }) {
                    currentStaticList.add(0, result)
                    addedAny = true
                }
            }
            if (addedAny) {
                _staticMediaItems.value = currentStaticList
            }
        }
    }

    private fun getInitialMediaItems(): List<DownloadItem> {
        return listOf(
            DownloadItem(
                title = "Top Hits 2024 Collection",
                url = "https://youtube.com/watch?v=tophits2024",
                category = "Video",
                size = "24.5 MB",
                progress = 0f,
                status = "PAUSED",
                duration = "12:30"
            ),
            DownloadItem(
                title = "Lofi Beats for Focus",
                url = "https://soundcloud.com/lofi-beats-focus",
                category = "Music",
                size = "5.1 MB",
                progress = 0f,
                status = "PAUSED",
                duration = "4:15",
                isAudioOnly = true
            ),
            DownloadItem(
                title = "Aesthetic Sunset Loop",
                url = "https://pexels.com/video/sunset-loop",
                category = "Featured",
                size = "15.2 MB",
                progress = 0f,
                status = "PAUSED",
                duration = "0:30"
            ),
            DownloadItem(
                title = "Funny Pets Compilation",
                url = "https://youtube.com/watch?v=funnypets",
                category = "Video",
                size = "32.0 MB",
                progress = 0f,
                status = "PAUSED",
                duration = "8:45"
            ),
            DownloadItem(
                title = "Cyberpunk Synthwave Mix",
                url = "https://soundcloud.com/cyberpunk-synthwave",
                category = "Music",
                size = "8.4 MB",
                progress = 0f,
                status = "PAUSED",
                duration = "6:20",
                isAudioOnly = true
            ),
            DownloadItem(
                title = "Cute Kitten Shorts",
                url = "https://instagram.com/p/cutekitten",
                category = "Social",
                size = "2.1 MB",
                progress = 0f,
                status = "PAUSED",
                duration = "0:15"
            ),
            DownloadItem(
                title = "Travel Vlog - Kyoto",
                url = "https://tiktok.com/@travelvlog/kyoto",
                category = "Social",
                size = "28.3 MB",
                progress = 0f,
                status = "PAUSED",
                duration = "5:40"
            ),
            DownloadItem(
                title = "Acoustic Guitar Cover",
                url = "https://jamendo.com/track/acousticcover",
                category = "Music",
                size = "3.8 MB",
                progress = 0f,
                status = "PAUSED",
                duration = "3:30",
                isAudioOnly = true
            ),
            DownloadItem(
                title = "Beautiful Drone Footage",
                url = "https://pixabay.com/videos/drone-footage",
                category = "Video",
                size = "45.0 MB",
                progress = 0f,
                status = "PAUSED",
                duration = "3:15"
            )
        )
    }
}

class VideoHubViewModelFactory(private val repository: DownloadRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VideoHubViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VideoHubViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
