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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.threadnetwork.GetAllActiveCredentialsRequest
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
    }
    val preferredLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == RESULT_OK) {
            networks += ThreadNetworkCredentials.fromIntentSenderResultData(it.data!!)
        } else {
            Log.e("TI", "Canceled")
        }

        loading = false
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
    var pendingData: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ThreadNetwork.getNetworkClient(this)
            .getAllActiveCredentials(GetAllActiveCredentialsRequest.newBuilder().build())
            .addOnSuccessListener { activeResult ->
                activeLauncher.launch(IntentSenderRequest.Builder(activeResult.intentSender!!).build())

                ThreadNetwork.getNetworkClient(this)
                    .preferredCredentials
                    .addOnSuccessListener { preferredResult ->
                        preferredLauncher.launch(IntentSenderRequest.Builder(preferredResult.intentSender!!).build())
                    }
                    .addOnFailureListener {
                        Log.e("TI", "Failed", it)
                        loading = false
                    }
            }
            .addOnFailureListener {
                Log.e("TI", "Failed", it)
                loading = false
            }

        setContent {
            ThreadInfoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Crossfade(
                        targetState = !loading,
                    ) { hasNetworks ->
                        if (hasNetworks) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = WindowInsets.systemBars.asPaddingValues(),
                            ) {
                                items(items = networks, key = { it.networkName }) { network ->
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
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}
