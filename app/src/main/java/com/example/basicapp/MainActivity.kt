package com.example.basicapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.basicapp.ui.theme.BasicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(val timestamp: String, val message: String, val isError: Boolean = false)
data class ChatMessage(val content: String, val isUser: Boolean, val timestamp: String = getCurrentTimestamp())

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BasicAppTheme {
                AppContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent() {
    val selectedTabIndex = remember { mutableIntStateOf(0) }
    val tabs = listOf("Connect", "Chat", "Logs")

    // Shared state across tabs
    val logs = remember { mutableStateListOf<LogEntry>() }
    val ipAddress = remember { mutableStateOf("192.168.1.147") }
    val port = remember { mutableStateOf("5000") }
    val isConnected = remember { mutableStateOf(false) }
    val connectionInProgress = remember { mutableStateOf(false) }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Local AI") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex.intValue,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex.intValue == index,
                        onClick = { selectedTabIndex.intValue = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTabIndex.intValue) {
                0 -> ConnectScreen(
                    ipAddress = ipAddress.value,
                    port = port.value,
                    isConnected = isConnected.value,
                    connectionInProgress = connectionInProgress.value,
                    onIpChanged = { ipAddress.value = it },
                    onPortChanged = { port.value = it },
                    onConnectClick = { ip, port ->
                        connectionInProgress.value = true
                        checkConnection(ip, port) { result, message ->
                            connectionInProgress.value = false
                            isConnected.value = result
                            val timestamp = getCurrentTimestamp()
                            if (result) {
                                logs.add(LogEntry(timestamp, "Successfully connected to $ip:$port"))
                            } else {
                                logs.add(LogEntry(timestamp, "Connection failed: $message", true))
                            }
                        }
                    }
                )
                1 -> ChatScreen(
                    messages = chatMessages,
                    isConnected = isConnected.value,
                    serverUrl = "http://${ipAddress.value}:${port.value}",
                    onMessageSent = { message ->
                        val userMessage = ChatMessage(message, true)
                        chatMessages.add(userMessage)
                        logs.add(LogEntry(getCurrentTimestamp(), "Sent message: $message"))

                        // Send to server and handle streaming response
                        streamResponse(
                            message = message,
                            serverUrl = "http://${ipAddress.value}:${port.value}",
                            onStreamStart = {
                                chatMessages.add(ChatMessage("", false))
                            },
                            onStreamUpdate = { partialResponse ->
                                val lastIndex = chatMessages.size - 1
                                if (lastIndex >= 0 && !chatMessages[lastIndex].isUser) {
                                    val updatedMessage = chatMessages[lastIndex].copy(content = partialResponse)
                                    chatMessages[lastIndex] = updatedMessage
                                }
                            },
                            onStreamComplete = {
                                logs.add(LogEntry(getCurrentTimestamp(), "Received complete response"))
                            },
                            onError = { error ->
                                logs.add(LogEntry(getCurrentTimestamp(), "Error: $error", true))
                            }
                        )
                    }
                )
                2 -> LogsScreen(logs = logs)
            }
        }
    }
}

@Composable
fun ConnectScreen(
    ipAddress: String,
    port: String,
    isConnected: Boolean,
    connectionInProgress: Boolean,
    onIpChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onConnectClick: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Connect to Local AI Server",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            value = ipAddress,
            onValueChange = onIpChanged,
            label = { Text("IP Address") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = port,
            onValueChange = onPortChanged,
            label = { Text("Port") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { onConnectClick(ipAddress, port) },
            enabled = !connectionInProgress,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (connectionInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp
                )
            }
            Text(if (isConnected) "Reconnect" else "Connect")
        }

        if (isConnected) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "✅ Connected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Server at $ipAddress:$port is reachable",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    isConnected: Boolean,
    serverUrl: String,
    onMessageSent: (String) -> Unit
) {
    val messageText = remember { mutableStateOf("") }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    if (messages.isNotEmpty()) {
        LaunchedEffect(messages.size) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Messages area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(messages) { message ->
                ChatMessageItem(message)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText.value,
                onValueChange = { messageText.value = it },
                placeholder = { Text("Type a message") },
                modifier = Modifier.weight(1f),
                enabled = isConnected,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (messageText.value.isNotBlank() && isConnected) {
                            onMessageSent(messageText.value)
                            messageText.value = ""
                        } else if (!isConnected) {
                            Toast.makeText(context, "Not connected to server", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            )

            IconButton(
                onClick = {
                    if (messageText.value.isNotBlank() && isConnected) {
                        onMessageSent(messageText.value)
                        messageText.value = ""
                    } else if (!isConnected) {
                        Toast.makeText(context, "Not connected to server", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send"
                )
            }
        }

        if (!isConnected) {
            Text(
                text = "Not connected to server. Please connect first.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (message.isUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (message.isUser)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    color = textColor
                )
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun LogsScreen(logs: List<LogEntry>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Logs",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No logs yet")
            }
        } else {
            LazyColumn {
                items(logs) { log ->
                    LogEntryItem(log)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(log: LogEntry) {
    val textColor = if (log.isError)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = log.message,
                color = textColor
            )
            Text(
                text = log.timestamp,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// Utility functions
fun getCurrentTimestamp(): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date())
}

fun checkConnection(ip: String, port: String, callback: (Boolean, String) -> Unit) {
    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        try {
            val portInt = port.toIntOrNull() ?: 8000
            val url = URL("http://$ip:$portInt/health")

            try {
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val responseBuilder = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        responseBuilder.append(line)
                    }
                    reader.close()

                    val responseData = responseBuilder.toString()
                    val serverInfo = JSONObject(responseData)
                    val serverStatus = serverInfo.getString("status")

                    withContext(Dispatchers.Main) {
                        if (serverStatus == "healthy") {
                            callback(true, "Connected successfully to AI server")
                        } else {
                            callback(false, "Server is not healthy: $responseData")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callback(false, "Server returned error code: $responseCode")
                    }
                }
                connection.disconnect()

            } catch (e: Exception) {
                val reachable = InetAddress.getByName(ip).isReachable(3000)

                withContext(Dispatchers.Main) {
                    if (reachable) {
                        callback(false, "IP is reachable but service isn't available: ${e.message}")
                    } else {
                        callback(false, "Server is not reachable: ${e.message}")
                    }
                }
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                callback(false, "Connection error: ${e.message ?: "Unknown error"}")
            }
        }
    }
}

fun streamResponse(
    message: String,
    serverUrl: String,
    onStreamStart: () -> Unit,
    onStreamUpdate: (String) -> Unit,
    onStreamComplete: () -> Unit,
    onError: (String) -> Unit
) {
    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                onStreamStart()
            }

            val url = URL("$serverUrl/chat")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            // Log the request body
            val requestBody = "{\"message\":\"${message.replace("\"", "\\\"").replace("\n", "\\n")}\"}"
            println("Request Body: $requestBody")

            val outputStream = connection.outputStream
            outputStream.write(requestBody.toByteArray())
            outputStream.close()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                withContext(Dispatchers.Main) {
                    onError("Server returned code $responseCode")
                }
                return@launch
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            var accumulatedResponse = ""
            var lastUpdateTime = System.currentTimeMillis()
            val updateInterval = 300L  // Update UI at most every 300 milliseconds

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) continue

                try {
                    val jsonObject = JSONObject(line)

                    when (jsonObject.getString("status")) {
                        "generating" -> {
                            var partialResponse = jsonObject.getString("response")

                            // Remove timestamps (assuming they are in a specific format)
                            partialResponse = partialResponse.replace(Regex("\\d{2}:\\d{2}:\\d{2}"), "")

                            // Add spaces between concatenated words
                            partialResponse = insertSpacesBetweenWords(partialResponse)

                            accumulatedResponse += partialResponse

                            // Debounce updates to avoid overwhelming the UI
                            if (System.currentTimeMillis() - lastUpdateTime >= updateInterval) {
                                withContext(Dispatchers.Main) {
                                    onStreamUpdate(accumulatedResponse)
                                }
                                lastUpdateTime = System.currentTimeMillis()
                            }
                        }
                        "complete" -> {
                            // Ensure final update is sent
                            withContext(Dispatchers.Main) {
                                onStreamUpdate(accumulatedResponse)
                                onStreamComplete()
                            }
                            break
                        }
                        "error" -> {
                            val errorMsg = if (jsonObject.has("error"))
                                jsonObject.getString("error")
                            else
                                "Unknown error"

                            withContext(Dispatchers.Main) {
                                onError(errorMsg)
                            }
                            break
                        }
                        "disconnected" -> {
                            withContext(Dispatchers.Main) {
                                onError("Server disconnected")
                            }
                            break
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError("Error parsing streaming response: ${e.message}")
                    }
                    break
                }
            }
            reader.close()
            connection.disconnect()

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Error streaming response: ${e.message}")
            }
        }
    }
}

fun insertSpacesBetweenWords(text: String): String {
    // Insert space before a capital letter that follows a lowercase letter
    var formattedText = text.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")

    // Insert space before a lowercase letter that follows a digit
    formattedText = formattedText.replace(Regex("(?<=\\d)(?=[a-zA-Z])"), " ")

    // Insert space when two words are unintentionally concatenated without spacing
    formattedText = formattedText.replace(Regex("(?<=[a-zA-Z])(?=[A-Z])"), " ")

    // Ensure spacing around punctuation marks like commas and periods
    formattedText = formattedText.replace(Regex("([,.!?])(?=[A-Za-z])"), "$1 ")

    return formattedText
}
