package com.example.localmusicassistant

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.KeyboardVoice
import androidx.compose.material3.icons.filled.Pause
import androidx.compose.material3.icons.filled.PlayArrow
import androidx.compose.material3.icons.filled.SkipNext
import androidx.compose.material3.icons.filled.SkipPrevious
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.SpeechStreamService
import java.io.File
import java.util.Locale

data class Song(
    val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val uri: Uri
)

class MainActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private var tts: TextToSpeech? = null

    // Vosk
    private var voskModel: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        // No-op; UI reflects availability
    }

    private fun requestPermissions() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT &lt; 33) {
            needed += Manifest.permission.READ_EXTERNAL_STORAGE
        } else {
            needed += Manifest.permission.READ_MEDIA_AUDIO
        }
        val toAsk = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toAsk.isNotEmpty()) {
            audioPermissionLauncher.launch(toAsk.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        volumeControlStream = AudioManager.STREAM_MUSIC

        player = ExoPlayer.Builder(this).build()
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                // Try to pick a pleasant female voice if available
                try {
                    val engineVoices = tts?.voices?.filter { v ->
                        v.locale.language == Locale.getDefault().language
                    }.orEmpty()

                    val preferred = engineVoices.firstOrNull { v ->
                        val name = v.name.lowercase()
                        name.contains("female") || name.contains("fem") || name.contains("en-us-x") || name.contains("gb-")
                    } ?: engineVoices.firstOrNull()

                    preferred?.let { tts?.voice = it }
                    tts?.setPitch(1.05f)
                    tts?.setSpeechRate(1.0f)
                } catch (_: Exception) {
                    // Fall back silently if selection fails
                    tts?.setPitch(1.05f)
                    tts?.setSpeechRate(1.0f)
                }
            }
        }

        requestPermissions()

        setContent {
            App(player = player,
                onMicClick = { toggleListening() },
                speak = { phrase -> speak(phrase) })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService?.stop()
        speechService = null
        recognizer?.close()
        voskModel?.close()
        player.release()
        tts?.shutdown()
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts-utterance")
    }

    private fun ensureModelReady(): Boolean {
        // Expect a Vosk model directory called "model" in the app's files dir
        // e.g., filesDir/model containing conf/model.conf, etc.
        val modelDir = File(filesDir, "model")
        if (!modelDir.exists()) return false
        return try {
            if (voskModel == null) {
                voskModel = Model(modelDir.absolutePath)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun toggleListening() {
        if (speechService != null) {
            speechService?.stop()
            speechService = null
            speak("Stopped listening")
            return
        }
        if (!ensureModelReady()) {
            speak("Speech model not found. See the README to install the Vosk model.")
            return
        }
        try {
            recognizer = Recognizer(voskModel, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening { resultJson ->
                handleRecognitionResult(resultJson)
            }
            speak("Listening")
        } catch (e: Exception) {
            speak("Failed to start listening")
        }
    }

    private fun handleRecognitionResult(resultJson: String) {
        // Vosk returns JSON like: {"text": "play music"}
        val text = Regex(""""text"\s*:\s*"([^"]*)"""").find(resultJson)?.groupValues?.getOrNull(1)
            ?.lowercase(Locale.getDefault())
            ?: return
        if (text.isBlank()) return

        runOnUiThread {
            when {
                text.contains("pause") -> {
                    player.pause()
                    speak("Paused")
                }
                text.contains("resume") || text.contains("continue") || text.contains("play") &amp;&amp; player.playWhenReady == false &amp;&amp; player.currentMediaItem != null -> {
                    player.play()
                    speak("Resumed")
                }
                text.contains("next") -> {
                    player.seekToNextMediaItem()
                    speak("Next")
                }
                text.contains("previous") || text.contains("back") -> {
                    player.seekToPreviousMediaItem()
                    speak("Previous")
                }
                text.contains("shuffle on") -> {
                    player.shuffleModeEnabled = true
                    speak("Shuffle on")
                }
                text.contains("shuffle off") -> {
                    player.shuffleModeEnabled = false
                    speak("Shuffle off")
                }
                text.contains("repeat on") -> {
                    player.repeatMode = Player.REPEAT_MODE_ALL
                    speak("Repeat on")
                }
                text.contains("repeat off") -> {
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    speak("Repeat off")
                }
                text.startsWith("play ") || text.contains("play music") -> {
                    val query = text.removePrefix("play ").trim()
                    lifecycleScope.launch {
                        val songs = querySongs(this@MainActivity, query.ifBlank { null })
                        if (songs.isNotEmpty()) {
                            setPlaylistAndPlay(songs)
                            val first = songs.first()
                            speak("Now playing ${first.artist ?: "Unknown"} — ${first.title}")
                        } else {
                            speak("No matches found")
                        }
                    }
                }
            }
        }
    }

    private suspend fun querySongs(context: Context, query: String? = null): List<Song> =
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM
            )
            val selection = buildString {
                append("${MediaStore.Audio.Media.IS_MUSIC}=1")
                if (!query.isNullOrBlank()) {
                    append(" AND (")
                    append("${MediaStore.Audio.Media.TITLE} LIKE ? OR ")
                    append("${MediaStore.Audio.Media.ARTIST} LIKE ? OR ")
                    append("${MediaStore.Audio.Media.ALBUM} LIKE ? )")
                }
            }
            val selectionArgs = if (!query.isNullOrBlank()) {
                Array(3) { "%$query%" }
            } else null

            val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

            val songs = mutableListOf<Song>()
            val collection = if (Build.VERSION.SDK_INT &gt;= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            context.contentResolver.query(
                collection, projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Title"
                    val artist = cursor.getString(artistCol)
                    val album = cursor.getString(albumCol)
                    val contentUri: Uri = ContentUris.withAppendedId(collection, id)
                    songs += Song(id, title, artist, album, contentUri)
                }
            }
            songs
        }

    private fun setPlaylistAndPlay(songs: List<Song>) {
        player.setMediaItems(songs.map { song ->
            MediaItem.Builder()
                .setUri(song.uri)
                .setMediaId(song.id.toString())
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                ).build()
        })
        player.prepare()
        player.playWhenReady = true
    }
}

@OptIn(UnstableApi::class)
@Composable
fun App(player: ExoPlayer, onMicClick: () -&gt; Unit, speak: (String) -&gt; Unit) {
    val context = LocalContext.current
    var songs by remember { mutableStateOf(listOf<Song>()) }
    var hasPermission by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf("—") }
    var currentArtist by remember { mutableStateOf("—") }

    LaunchedEffect(Unit) {
        hasPermission = checkAllPermissions(context)
        if (hasPermission) {
            songs = (context as MainActivity).querySongs(context)
            if (songs.isNotEmpty()) {
                (context as MainActivity).setPlaylistAndPlay(songs)
                isPlaying = player.isPlaying
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                isPlaying = player.isPlaying
                val md = player.mediaMetadata
                currentTitle = md.title?.toString() ?: "—"
                currentArtist = md.artist?.toString() ?: "—"
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Local Music Assistant") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.KeyboardVoice,
                    contentDescription = "Microphone",
                    modifier = Modifier
                        .size(96.dp)
                        .clickable { onMicClick() }
                        .padding(8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxSize().padding(top = 0.dp).weight(1f, fill = false)
            ) {
                IconButton(onClick = { player.seekToPreviousMediaItem() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                }
                IconButton(onClick = {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
                IconButton(onClick = { player.seekToNextMediaItem() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }

            if (!hasPermission) {
                Button(onClick = {
                    (context as MainActivity).requestPermissions()
                    hasPermission = checkAllPermissions(context)
                }) {
                    Text("Grant permissions")
                }
            }
        }
    }
}

private fun checkAllPermissions(context: Context): Boolean {
    val audioGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    val storageGranted = if (Build.VERSION.SDK_INT &lt; 33) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    return audioGranted &amp;&amp; storageGranted
}