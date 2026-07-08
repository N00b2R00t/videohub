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

    // AI state
    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse.asStateFlow()

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

    // AI Query integration
    fun searchOrAskAi(prompt: String) {
        if (prompt.isBlank()) return
        _searchQuery.value = prompt
        _isAiLoading.value = true
        _aiResponse.value = null

        viewModelScope.launch {
            val systemInstruction = """
                You are VidMate's intelligent VideoHub AI Assistant.
                The user is searching for content or asking where VidMate gets free video/music files.
                - Guide the user with professional and accurate information.
                - Explain that VidMate acts as a specialized crawler and browser helper, pulling publicly hosted media streams from supported platforms like YouTube (with user-provided links), SoundCloud, Archive.org, Jamendo, Pixabay, Pexels, and custom web sources.
                - Suggest legal free resources (like Jamendo, Archive.org, Pixabay, Pexels) for direct downloads.
                - Format your response clearly in structured markdown with bullet points.
                - Offer to simulate a download card if they are searching for a specific song/video name! Say "I can generate a direct local download link for: <name>" if they typed a title.
            """.trimIndent()

            val response = GeminiClient.generateContent(prompt, systemInstruction)
            _aiResponse.value = response
            _isAiLoading.value = false

            // Try to extract a title to automatically offer as a download!
            val hasVideoKeywords = prompt.contains(".mp4") || prompt.contains(".mp3") || prompt.contains("download") || prompt.length > 3
            if (hasVideoKeywords && !prompt.contains("where", ignoreCase = true)) {
                // Generate a custom download card based on what they asked!
                createDynamicMediaCard(prompt)
            }
        }
    }

    private fun createDynamicMediaCard(prompt: String) {
        // Parse a nice title
        val title = prompt.substringBefore("?").substringBefore("&")
            .replace("https://", "")
            .replace("http://", "")
            .replace("www.", "")
            .replace(".com", "")
            .replace("/", " ")
            .trim()
            .replaceFirstChar { it.uppercase() }

        val isMusic = prompt.contains("song", ignoreCase = true) || prompt.contains("music", ignoreCase = true) || prompt.contains(".mp3", ignoreCase = true) || prompt.contains("audio", ignoreCase = true)
        
        val dynamicItem = DownloadItem(
            id = 0,
            title = if (title.length > 40) title.take(40) + "..." else title,
            url = prompt,
            category = if (isMusic) "Music" else "Video",
            size = "${Random.nextInt(4, 35)}.${Random.nextInt(0, 9)} MB",
            progress = 0f,
            status = "PAUSED", // Initial state ready to download
            duration = "${Random.nextInt(1, 10)}:${Random.nextInt(10, 59)}",
            isAudioOnly = isMusic
        )

        // Add to current staticMediaItems list so it appears in list
        val currentList = _staticMediaItems.value.toMutableList()
        // Avoid duplicate titles
        if (!currentList.any { it.title.equals(dynamicItem.title, ignoreCase = true) }) {
            currentList.add(0, dynamicItem)
            _staticMediaItems.value = currentList
            viewModelScope.launch {
                _uiEventMessage.emit("Generated instant download card for: ${dynamicItem.title}!")
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
