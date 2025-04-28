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
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(val timestamp: String, val message: String, val isError: Boolean = false)
data class ChatMessage(val content: String, val isUser: Boolean, val timestamp: String = getCurrentTimestamp())

class MainActivity : ComponentActivity() {
    private lateinit var serviceDiscoveryManager: ServiceDiscoveryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        serviceDiscoveryManager = ServiceDiscoveryManager(this)

        setContent {
            BasicAppTheme {
                AppContent(serviceDiscoveryManager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceDiscoveryManager.stopDiscovery()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(serviceDiscoveryManager: ServiceDiscoveryManager) {
    val selectedTabIndex = remember { mutableIntStateOf(0) }
    val tabs = listOf("Services", "Chat", "Logs")

    val discoveredServices by serviceDiscoveryManager.discoveredServices.collectAsState()
    val logs = remember { mutableStateListOf<LogEntry>() }
    val isConnected = remember { mutableStateOf(false) }
    val connectionInProgress = remember { mutableStateOf(false) }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    val selectedService = remember { mutableStateOf<DiscoveredService?>(null) }

    LaunchedEffect(Unit) {
        serviceDiscoveryManager.startDiscovery()
    }

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
                0 -> ServicesScreen(
                    services = discoveredServices,
                    selectedService = selectedService.value,
                    isConnected = isConnected.value,
                    connectionInProgress = connectionInProgress.value,
                    onServiceSelected = { service ->
                        selectedService.value = service
                        connectionInProgress.value = true
                        checkConnection(service.host, service.port.toString()) { result, message ->
                            connectionInProgress.value = false
                            isConnected.value = result
                            val timestamp = getCurrentTimestamp()
                            if (result) {
                                logs.add(LogEntry(timestamp, "Successfully connected to ${service.host}:${service.port}"))
                            } else {
                                logs.add(LogEntry(timestamp, "Connection failed: $message", true))
                            }
                        }
                    }
                )
                1 -> ChatScreen(
                    messages = chatMessages,
                    isConnected = isConnected.value,
                    onMessageSent = { message ->
                        val userMessage = ChatMessage(message, true)
                        chatMessages.add(userMessage)
                        logs.add(LogEntry(getCurrentTimestamp(), "Sent message: $message"))

                        selectedService.value?.let { service ->
                            streamResponse(
                                message = message,
                                serverUrl = "http://${service.host}:${service.port}",
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
                    }
                )
                2 -> LogsScreen(logs = logs)
            }
        }
    }
}

@Composable
fun ServicesScreen(
    services: List<DiscoveredService>,
    selectedService: DiscoveredService?,
    isConnected: Boolean,
    connectionInProgress: Boolean,
    onServiceSelected: (DiscoveredService) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Available Services",
            style = MaterialTheme.typography.headlineSmall
        )

        if (services.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Searching for services...")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(services) { service ->
                    ServiceItem(
                        service = service,
                        isSelected = service == selectedService,
                        isConnected = isConnected && service == selectedService,
                        connectionInProgress = connectionInProgress && service == selectedService,
                        onClick = { onServiceSelected(service) }
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceItem(
    service: DiscoveredService,
    isSelected: Boolean,
    isConnected: Boolean,
    connectionInProgress: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = service.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${service.host}:${service.port}",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = onClick,
                enabled = !connectionInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                if (connectionInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(when {
                    isConnected -> "Connected"
                    connectionInProgress -> "Connecting..."
                    isSelected -> "Reconnect"
                    else -> "Connect"
                })
            }
        }
    }
}

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    isConnected: Boolean,
    onMessageSent: (String) -> Unit
) {
    val messageText = remember { mutableStateOf("") }
    val context = LocalContext.current
    val listState = rememberLazyListState()

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
                    imageVector = Icons.AutoMirrored.Filled.Send,
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
                    color = if (message.isUser)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
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
                color = if (log.isError)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = log.timestamp,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

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
                withContext(Dispatchers.Main) {
                    callback(false, "Connection error: ${e.message ?: "Unknown error"}")
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
            val updateInterval = 300L

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) continue

                try {
                    val jsonObject = JSONObject(line)

                    when (jsonObject.getString("status")) {
                        "generating" -> {
                            var partialResponse = jsonObject.getString("response")
                            partialResponse = partialResponse.replace(Regex("\\d{2}:\\d{2}:\\d{2}"), "")
                            partialResponse = insertSpacesBetweenWords(partialResponse)
                            accumulatedResponse += partialResponse

                            if (System.currentTimeMillis() - lastUpdateTime >= updateInterval) {
                                withContext(Dispatchers.Main) {
                                    onStreamUpdate(accumulatedResponse)
                                }
                                lastUpdateTime = System.currentTimeMillis()
                            }
                        }
                        "complete" -> {
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
    var formattedText = text.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
    formattedText = formattedText.replace(Regex("(?<=\\d)(?=[a-zA-Z])"), " ")
    formattedText = formattedText.replace(Regex("(?<=[a-zA-Z])(?=[A-Z])"), " ")
    formattedText = formattedText.replace(Regex("([,.!?])(?=[A-Za-z])"), "$1 ")
    return formattedText
}