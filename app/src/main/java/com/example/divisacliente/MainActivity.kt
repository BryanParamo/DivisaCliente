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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
 * Obtiene la lista de monedas únicas desde el ContentProvider.
 */
@Composable
fun distinctCurrencies(): State<List<String>> {
    val context = LocalContext.current
    return produceState(initialValue = emptyList()) {
        val providerUri = Uri.parse("content://com.example.divisa.provider/exchange_rates")
        val cursor: Cursor? = context.contentResolver.query(
            providerUri,
            arrayOf("currency"), // Solo la columna "currency"
            null,
            null,
            "currency ASC"
        )

        val currencySet = mutableSetOf<String>()
        cursor?.use {
            val columnIndex = it.getColumnIndex("currency")
            if (columnIndex != -1) {
                while (it.moveToNext()) {
                    val currency = it.getString(columnIndex)
                    if (!currency.isNullOrBlank()) {
                        currencySet.add(currency)
                    }
                }
            } else {
                Log.e(
                    "distinctCurrencies",
                    "La columna 'currency' no existe. Columnas disponibles: ${
                        cursor.columnNames.joinToString()
                    }"
                )
            }
        }
        value = currencySet.toList()
    }
}

/**
 * Selector de rango de fechas con DatePickerDialog.
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

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text(
            text = "Fecha de inicio: ${dateFormat.format(Date(startDate))}",
            modifier = Modifier.clickable { showStartPicker = true }
        )
        Text(
            text = "Fecha de fin: ${dateFormat.format(Date(endDate))}",
            modifier = Modifier.clickable { showEndPicker = true }
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
 * Selector de moneda con BottomSheet y campo de búsqueda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySelector(
    currencies: List<String>,
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredCurrencies = currencies.filter { it.contains(searchQuery, ignoreCase = true) }

    // Botón (Surface) que muestra la divisa seleccionada y abre la hoja modal
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showSheet = true }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(text = selectedCurrency, modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Seleccionar divisa"
            )
        }
    }

    // ModalBottomSheet para mostrar la lista de divisas filtrables
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Selecciona una divisa",
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar divisa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                LazyColumn {
                    items(filteredCurrencies) { currency ->
                        ListItem(
                            headlineContent = { Text(currency) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCurrencySelected(currency)
                                    showSheet = false
                                }
                                .padding(12.dp)
                        )
                        Divider()
                    }
                }
                TextButton(
                    onClick = { showSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}

/**
 * Pantalla principal: Selector de moneda, selector de fechas y el gráfico.
 */
@Composable
fun MainScreen() {
    val currencies by distinctCurrencies()
    var selectedCurrency by remember { mutableStateOf(if (currencies.isNotEmpty()) currencies.first() else "USD") }
    var startDate by remember { mutableStateOf(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L) }
    var endDate by remember { mutableStateOf(System.currentTimeMillis()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Gráfico de Tasa de Cambio (App Cliente)")

        // Selector de moneda (usa la versión con BottomSheet y búsqueda)
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

        // Gráfico
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

    Log.d("ExchangeRateChartClient", "startDate: $startDate, endDate: $endDate")

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

        cursorState.value?.use { cursor ->
            val timestampIndex = cursor.getColumnIndex("timestamp")
            val rateIndex = cursor.getColumnIndex("rate")
            if (timestampIndex != -1 && rateIndex != -1) {
                while (cursor.moveToNext()) {
                    val ts = cursor.getLong(timestampIndex)
                    val r = cursor.getDouble(rateIndex)
                    timestamps.add(ts)
                    rates.add(r)
                }
            } else {
                Log.e("ExchangeRateChartClient", "Columnas 'timestamp' o 'rate' no encontradas")
            }
        }

        // Convertir timestamp de milisegundos a horas desde el primer registro
        if (timestamps.isNotEmpty()) {
            val minTimestamp = timestamps.minOrNull() ?: 0L
            for (i in timestamps.indices) {
                val diffMillis = timestamps[i] - minTimestamp
                val xValue = (diffMillis / 1000f) / (60f * 60f) // Convertir de ms a horas
                val yValue = rates[i].toFloat()
                listOfEntries.add(Entry(xValue, yValue))
            }
        }

        // Log de depuración
        listOfEntries.forEachIndexed { index, entry ->
            Log.d("ExchangeRateChartClient", "Punto $index -> X: ${entry.x}, Y: ${entry.y}")
        }

        listOfEntries
    }

    // Graficar en un AndroidView
    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                // Se crea el LineChart una sola vez
                LineChart(ctx).apply {
                    setBackgroundColor(android.graphics.Color.LTGRAY)
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
                }
            },
            update = { chart ->
                // Actualiza los datos del gráfico
                val dataSet = LineDataSet(entries, "Tasa de $currency vs MXN").apply {
                    setDrawCircles(true)
                    setDrawValues(true)
                    circleRadius = 3f
                    lineWidth = 2f
                    color = android.graphics.Color.RED
                    setCircleColor(android.graphics.Color.BLACK)
                    setDrawFilled(true)
                    fillColor = android.graphics.Color.BLUE
                    fillAlpha = 120
                }

                chart.data = LineData(dataSet)

                // Ajustar ejes si hay datos
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
