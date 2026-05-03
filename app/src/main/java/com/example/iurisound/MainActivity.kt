package com.example.iurisound

import android.Manifest
import android.content.Intent
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private var recorder: MediaRecorder? = null

    private fun iniciarSensor() {
        try {
            liberarRecorder()
            val cacheFile = File(cacheDir, "temp.3gp")
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(cacheFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun liberarRecorder() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) { }
        recorder = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        launcher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                IuriSoundApp(
                    onStart = { iniciarSensor() },
                    onStop = { liberarRecorder() },
                    getAmplitud = { recorder?.maxAmplitude ?: 0 }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        liberarRecorder()
    }
}

@Composable
fun IuriSoundApp(onStart: () -> Unit, onStop: () -> Unit, getAmplitud: () -> Int) {
    val context = LocalContext.current
    var decibelios by remember { mutableStateOf(0.0) }
    var tiempoRestante by remember { mutableStateOf(0) }
    var escaneando by remember { mutableStateOf(false) }
    var mostrarResultado by remember { mutableStateOf(false) }

    var promedioFinal by remember { mutableStateOf(0.0) }
    var picoMaximo by remember { mutableStateOf(0.0) }
    var duracionTotal by remember { mutableStateOf(0) }

    val verdeIuri = Color(0xFF81C784)

    LaunchedEffect(escaneando) {
        if (escaneando) {
            onStart()
            var sumaDb = 0.0
            var maxDb = 0.0
            var contador = 0
            duracionTotal = tiempoRestante

            while (tiempoRestante > 0 && escaneando) {
                delay(200)
                val amp = try { getAmplitud() } catch (e: Exception) { 0 }
                if (amp > 0) {
                    val db = 20 * kotlin.math.log10(amp.toDouble() / 10.0)
                    decibelios = if (db < 0) 0.0 else db

                    sumaDb += decibelios
                    if (decibelios > maxDb) maxDb = decibelios
                    contador++
                }
            }
            promedioFinal = if (contador > 0) sumaDb / contador else 0.0
            picoMaximo = maxDb
            escaneando = false
            mostrarResultado = true
            onStop()
        }
    }

    LaunchedEffect(escaneando) {
        while (escaneando && tiempoRestante > 0) {
            delay(1000)
            tiempoRestante -= 1
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("IuriSound", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)

        if (mostrarResultado) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("REPORTE DE ESCANEO", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Promedio: ${promedioFinal.toInt()} dB", fontSize = 32.sp, color = Color.Cyan)
                    Text("Pico máximo: ${picoMaximo.toInt()} dB", fontSize = 18.sp, color = Color.LightGray)

                    val enNorma = promedioFinal < 75
                    Text(
                        text = if (enNorma) "DENTRO DE LA NORMA" else "EXCESO DE RUIDO",
                        color = if (enNorma) verdeIuri else Color(0xFFE57373),
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 10.dp))

                    OutlinedButton(
                        onClick = {
                            val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                            val mensaje = """
                                🎙️ *Reporte de Ruido - IuriSound*
                                📅 Fecha: $fecha
                                ⏱️ Duración: $duracionTotal segundos
                                📊 Promedio: ${promedioFinal.toInt()} dB
                                🔊 Pico Máximo: ${picoMaximo.toInt()} dB
                                ⚠️ Estado: ${if (enNorma) "Normal" else "Fuera de norma"}
                            """.trimIndent()

                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, mensaje)
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir reporte"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color.Cyan),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("COMPARTIR REPORTE", fontWeight = FontWeight.Bold)
                    }

                    TextButton(onClick = { mostrarResultado = false }) {
                        Text("Volver a medir", color = Color.Gray)
                    }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (escaneando) "${decibelios.toInt()} dB" else "-- dB",
                    fontSize = 80.sp,
                    color = if (decibelios > 75) Color(0xFFE57373) else verdeIuri
                )
                if (escaneando) {
                    Text("Tiempo: ${tiempoRestante}s", color = Color.White)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), color = Color.Cyan)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(
                onClick = {
                    tiempoRestante = 15
                    mostrarResultado = false
                    escaneando = true
                },
                enabled = !escaneando,
                border = BorderStroke(2.dp, verdeIuri),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White, // TEXTO EN BLANCO
                    disabledContentColor = Color.DarkGray
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            ) {
                Text("15 Seg", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    tiempoRestante = 60
                    mostrarResultado = false
                    escaneando = true
                },
                enabled = !escaneando,
                border = BorderStroke(2.dp, verdeIuri),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White, // TEXTO EN BLANCO
                    disabledContentColor = Color.DarkGray
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            ) {
                Text("1 Min", fontWeight = FontWeight.Bold)
            }
        }
    }
}
