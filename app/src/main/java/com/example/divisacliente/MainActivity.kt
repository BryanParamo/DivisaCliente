package com.example.divisacliente

import android.app.DatePickerDialog
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.divisacliente.ui.theme.DivisaClienteTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DivisaClienteTheme {
                MainScreen()
            }
        }
    }
}

/**
 * Consulta el ContentProvider y retorna un State con la lista de monedas únicas.
 */
@Composable
fun distinctCurrencies(): State<List<String>> {
    val context = LocalContext.current
    return produceState(initialValue = emptyList<String>()) {
        val providerUri = Uri.parse("content://com.example.divisa.provider/exchange_rates")
        val cursor: Cursor? = context.contentResolver.query(
            providerUri,
            arrayOf("currency"), // Solo la columna "currency"
            null,
            null,
            null
        )
        val currencySet = mutableSetOf<String>()
        cursor?.use { cur ->
            if (cur.moveToFirst()) {
                do {
                    val currency = cur.getString(cur.getColumnIndex("currency"))
                    if (!currency.isNullOrBlank()) {
                        currencySet.add(currency)
                    }
                } while (cur.moveToNext())
            }
        }
        value = currencySet.toList()
        Log.d("distinctCurrencies", "Monedas encontradas: $value")
    }
}

/**
 * Selector de rango de fechas usando DatePickerDialog.
 */
@Composable
fun DateRangeSelector(
    startDate: Long,
    endDate: Long,
    onStartDateChanged: (Long) -> Unit,
    onEndDateChanged: (Long) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Fecha de inicio: ${dateFormat.format(Date(startDate))}",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showStartPicker = true }
                .padding(8.dp)
        )
        Text(
            text = "Fecha de fin: ${dateFormat.format(Date(endDate))}",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showEndPicker = true }
                .padding(8.dp)
        )
    }

    if (showStartPicker) {
        val calendar = Calendar.getInstance().apply { timeInMillis = startDate }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                }
                onStartDateChanged(newCalendar.timeInMillis)
                showStartPicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    if (showEndPicker) {
        val calendar = Calendar.getInstance().apply { timeInMillis = endDate }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 23, 59, 59)
                }
                onEndDateChanged(newCalendar.timeInMillis)
                showEndPicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}

/**
 * Selector de moneda: muestra un Dropdown con las monedas disponibles.
 */
@Composable
fun CurrencySelector(
    currencies: List<String>,
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier
        .fillMaxWidth()
        .clickable { expanded = true }
        .padding(vertical = 8.dp)
    ) {
        Text(text = "Moneda seleccionada: $selectedCurrency")
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            currencies.forEach { currency ->
                DropdownMenuItem(
                    text = { Text(currency) },
                    onClick = {
                        onCurrencySelected(currency)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Pantalla principal que muestra el selector de moneda, el selector de fechas y el gráfico.
 */
@Composable
fun MainScreen() {
    val currencies by distinctCurrencies() // Consulta dinámica de monedas
    // Estado para la moneda seleccionada: si no hay monedas, por defecto "USD"
    var selectedCurrency by remember { mutableStateOf(if (currencies.isNotEmpty()) currencies.first() else "USD") }

    // Estados para el rango de fechas (por defecto: últimos 7 días)
    var startDate by remember { mutableStateOf(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L) }
    var endDate by remember { mutableStateOf(System.currentTimeMillis()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Gráfico de Tasa de Cambio (App Cliente)")

        // Selector de moneda
        if (currencies.isNotEmpty()) {
            CurrencySelector(
                currencies = currencies,
                selectedCurrency = selectedCurrency,
                onCurrencySelected = { newCurrency -> selectedCurrency = newCurrency }
            )
        } else {
            Text("No se encontraron monedas.")
        }

        // Selector de rango de fechas
        DateRangeSelector(
            startDate = startDate,
            endDate = endDate,
            onStartDateChanged = { startDate = it },
            onEndDateChanged = { endDate = it }
        )

        // Gráfico: ocupa el resto de la pantalla
        ExchangeRateChartClient(
            currency = selectedCurrency,
            startDate = startDate,
            endDate = endDate,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

/**
 * Consulta el ContentProvider filtrando por [currency] y el rango de fechas, y grafica los datos.
 */
@Composable
fun ExchangeRateChartClient(
    currency: String,
    startDate: Long,
    endDate: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // URI del ContentProvider de la App A
    val providerUri = Uri.parse("content://com.example.divisa.provider/exchange_rates")
    // Filtro: currency = ? AND timestamp BETWEEN ? AND ?
    val selection = "currency = ? AND timestamp BETWEEN ? AND ?"
    val selectionArgs = arrayOf(currency, startDate.toString(), endDate.toString())

    Log.d("ExchangeRateChartClient", "startDate original: $startDate, convertido: ${(startDate / 1000)}")
    Log.d("ExchangeRateChartClient", "endDate original: $endDate, convertido: ${(endDate / 1000)}")


    // Consulta asíncrona del ContentProvider
    val cursorState = produceState<Cursor?>(initialValue = null, key1 = currency, key2 = startDate, key3 = endDate) {
        val c = context.contentResolver.query(
            providerUri,
            null,
            selection,
            selectionArgs,
            "timestamp ASC"
        )
        Log.d("ExchangeRateChartClient", "Cursor count: ${c?.count ?: 0}")
        value = c
    }

    // Convertir el Cursor en una lista de Entry (X = tiempo, Y = rate)
    val entries = remember(cursorState.value) {
        val listOfEntries = mutableListOf<Entry>()
        val timestamps = mutableListOf<Long>()
        val rates = mutableListOf<Double>()

        cursorState.value?.let { cursor ->
            val timestampIndex = cursor.getColumnIndex("timestamp")
            val rateIndex = cursor.getColumnIndex("rate")
            if (cursor.moveToFirst()) {
                do {
                    val ts = cursor.getLong(timestampIndex)
                    val r = cursor.getDouble(rateIndex)
                    timestamps.add(ts)
                    rates.add(r)
                } while (cursor.moveToNext())
            }
        }

        // Convertir timestamp de milisegundos a horas desde el primer registro
        if (timestamps.isNotEmpty()) {
            val minTimestamp = timestamps.minOrNull() ?: 0L
            for (i in timestamps.indices) {
                val diffMillis = timestamps[i] - minTimestamp
                val xValue = (diffMillis / 1000f) / (60f * 60f) // Convertir de segundos a horas
                val yValue = rates[i].toFloat()

                listOfEntries.add(Entry(xValue, yValue))
            }
        }

        // LOG para depuración
        listOfEntries.forEachIndexed { index, entry ->
            Log.d("ExchangeRateChartClient", "Punto $index -> X: ${entry.x}, Y: ${entry.y}")
        }


        listOfEntries
    }

    // Graficar
    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx -> LineChart(ctx).apply {
                setBackgroundColor(android.graphics.Color.LTGRAY)
                // Configuraciones iniciales (ejes, zoom, etc.) que no dependen de los datos
                xAxis.setDrawGridLines(true)
                axisLeft.setDrawGridLines(true)
                axisRight.setDrawGridLines(true)
                description.isEnabled = false
                setAutoScaleMinMaxEnabled(true)
                setPinchZoom(true)
                isDragEnabled = true
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}h"
                    }
                }
            } },
            update = { chart ->
                // Crear o actualizar el dataset con las nuevas entradas
                val dataSet = LineDataSet(entries, "Tasa de $currency vs MXN").apply {
                    setDrawCircles(true)
                    setDrawValues(true)
                    circleRadius = 2f  // 🔴 Tamaño grande para ver mejor los punto
                    lineWidth = 1f      // 🔵 Línea más gruesa
                    color = android.graphics.Color.RED
                    setCircleColor(android.graphics.Color.BLACK)
                    setDrawFilled(true)
                    fillColor = android.graphics.Color.BLUE //RELLENO HACIA ABAJO DE LA GRAFICA
                    fillAlpha = 120
                }
                chart.data = LineData(dataSet)

                // Configurar los límites de los ejes dinámicamente según las entradas
                if (entries.isNotEmpty()) {
                    val minX = entries.minOf { it.x }
                    val maxX = entries.maxOf { it.x }
                    chart.xAxis.axisMinimum = minX
                    chart.xAxis.axisMaximum = maxX

                    val minY = entries.minOf { it.y }
                    val maxY = entries.maxOf { it.y }
                    if (minY == maxY) {
                        chart.axisLeft.axisMinimum = minY * 0.95f
                        chart.axisLeft.axisMaximum = maxY * 1.05f
                        chart.axisRight.axisMinimum = minY * 0.95f
                        chart.axisRight.axisMaximum = maxY * 1.05f
                    } else {
                        chart.axisLeft.axisMinimum = minY
                        chart.axisLeft.axisMaximum = maxY
                        chart.axisRight.axisMinimum = minY
                        chart.axisRight.axisMaximum = maxY
                    }
                }

                chart.notifyDataSetChanged()
                chart.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

    }
}


