package dev.zwander.threadinfo

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Task
import com.google.android.gms.threadnetwork.GetAllActiveCredentialsRequest
import com.google.android.gms.threadnetwork.IntentSenderResult
import com.google.android.gms.threadnetwork.ThreadNetwork
import com.google.android.gms.threadnetwork.ThreadNetworkCredentials
import dev.zwander.threadinfo.ui.theme.ThreadInfoTheme

class MainActivity : ComponentActivity() {
    val activeLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == RESULT_OK) {
            val allCreds = ThreadNetworkCredentials.parseListFromIntentSenderResultData(it.data!!)

            networks += allCreds
        } else {
            Log.e("TI", "Canceled")
        }

        stopLoadingIfPossible()
    }
    val preferredLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == RESULT_OK) {
            networks += ThreadNetworkCredentials.fromIntentSenderResultData(it.data!!)
        } else {
            Log.e("TI", "Canceled")
        }

        stopLoadingIfPossible()
    }
    val byIdLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == RESULT_OK) {
            networks += ThreadNetworkCredentials.fromIntentSenderResultData(it.data!!)
        } else {
            Log.e("TI", "Canceled")
        }

        showingPanIdDialog = false
    }
    val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        uri?.let {
            contentResolver.openOutputStream(it)?.use { output ->
                pendingData?.toHexString()?.let { hex -> output.write(hex.toByteArray()) }
            }
        }
    }

    var networks by mutableStateOf(listOf<ThreadNetworkCredentials>())
    var loading by mutableStateOf(true)
    var showingPanIdDialog by mutableStateOf(false)
    var pendingData: ByteArray? = null

    val tasks = mutableListOf<Task<IntentSenderResult>>()

    private fun stopLoadingIfPossible() {
        if (tasks.all { it.isComplete || it.isCanceled }) {
            loading = false
            tasks.clear()
        }
    }

    private fun runTasks() {
        loading = true
        networks = listOf()

        tasks.add(
            ThreadNetwork.getNetworkClient(this)
                .preferredCredentials
                .addOnSuccessListener { preferredResult ->
                    preferredResult.intentSender?.let {
                        preferredLauncher.launch(IntentSenderRequest.Builder(it).build())
                    } ?: stopLoadingIfPossible()
                }
                .addOnFailureListener {
                    Log.e("TI", "Failed preferred", it)
                    stopLoadingIfPossible()
                },
        )

        tasks.add(
            ThreadNetwork.getNetworkClient(this)
                .getAllActiveCredentials(GetAllActiveCredentialsRequest.newBuilder().build())
                .addOnSuccessListener { activeResult ->
                    activeResult.intentSender?.let {
                        activeLauncher.launch(IntentSenderRequest.Builder(it).build())
                    } ?: stopLoadingIfPossible()
                }
                .addOnFailureListener {
                    Log.e("TI", "Failed active", it)
                    stopLoadingIfPossible()
                },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        runTasks()

        setContent {
            ThreadInfoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Crossfade(
                        targetState = !loading,
                    ) { hasNetworks ->
                        if (hasNetworks) {
                            PullToRefreshBox(
                                isRefreshing = false,
                                onRefresh = { runTasks() },
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth()
                                            .weight(1f),
                                        contentPadding = WindowInsets.systemBars
                                            .only(
                                                WindowInsetsSides.Top +
                                                        WindowInsetsSides.Start +
                                                        WindowInsetsSides.End,
                                            )
                                            .asPaddingValues(),
                                    ) {
                                        items(items = networks.distinctBy { it.panId }, key = { it.networkName }) { network ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth()
                                                    .heightIn(min = 64.dp)
                                                    .clickable {
                                                        pendingData = network.activeOperationalDataset
                                                        saveLauncher.launch("${network.networkName}.tlv")
                                                    }
                                                    .padding(
                                                        horizontal = 8.dp,
                                                        vertical = 16.dp,
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = network.networkName,
                                                )
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(16.dp)
                                            .padding(
                                                WindowInsets.systemBars
                                                    .only(
                                                        WindowInsetsSides.Bottom,
                                                    )
                                                    .asPaddingValues()
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                showingPanIdDialog = true
                                            },
                                        ) {
                                            Text(text = stringResource(R.string.add_by_pan_id))
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    if (showingPanIdDialog) {
                        var fieldText by remember {
                            mutableStateOf("")
                        }

                        AlertDialog(
                            onDismissRequest = {
                                showingPanIdDialog = false
                            },
                            title = { Text(text = stringResource(R.string.add_by_pan_id)) },
                            text = {
                                Column {
                                    OutlinedTextField(
                                        value = fieldText,
                                        onValueChange = {
                                            fieldText = it
                                        },
                                        placeholder = {
                                            Text(text = stringResource(R.string.pan_id_hint))
                                        },
                                    )

                                    Text(
                                        text = stringResource(R.string.add_by_pan_id_desc),
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        ThreadNetwork.getNetworkClient(this)
                                            .getCredentialsByExtendedPanId(fieldText.split(",").map { it.toByte() }.toByteArray())
                                            .addOnSuccessListener { result ->
                                                result.intentSender?.let {
                                                    byIdLauncher.launch(
                                                        IntentSenderRequest.Builder(it).build(),
                                                    )
                                                } ?: run {
                                                    showingPanIdDialog = false
                                                }
                                            }
                                            .addOnFailureListener {
                                                Log.e("TI", "Failed to add by PAN ID", it)
                                                showingPanIdDialog = false
                                            }
                                    },
                                ) {
                                    Text(text = stringResource(R.string.add))
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showingPanIdDialog = false
                                    },
                                ) {
                                    Text(text = stringResource(R.string.cancel))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
