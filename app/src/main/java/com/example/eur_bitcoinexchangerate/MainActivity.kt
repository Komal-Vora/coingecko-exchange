package com.example.eur_bitcoinexchangerate

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    private val moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val historicalData = mutableStateListOf<HistoricalData>()
    private val currentPrice = mutableStateOf(0.0)
    private val lastUpdated = mutableStateOf("")
    private val currencies = listOf("eur", "usd", "gbp", "jpy", "cad", "aud")


    private var selectedCurrencyIndex by mutableStateOf(0)
    private var dropdownExpanded by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Column(
                modifier = Modifier
                    .background(Color.White) // Set background color here
                    .fillMaxSize()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Currency: ",
                        style = MaterialTheme.typography.body1,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                    )

                    Box(
                        modifier = Modifier.clickable { dropdownExpanded = !dropdownExpanded }
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currencies[selectedCurrencyIndex].toUpperCase(Locale.getDefault()),
                            style = MaterialTheme.typography.body1,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(5.dp)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null,
                            modifier = Modifier
                                .clickable {
                                    dropdownExpanded = true
                                })
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        currencies.forEachIndexed { index, currency ->
                            DropdownMenuItem(onClick = {
                                selectedCurrencyIndex = index
                                dropdownExpanded = false // Close dropdown after selection
                                fetchData()
                                fetchDatacurrent()
                            }) {
                                Text(text = currency.toUpperCase(Locale.getDefault()))
                            }
                        }
                    }
                }

                Text(
                    text = "Current Price: ${currencies[selectedCurrencyIndex].toUpperCase(Locale.getDefault())} ${currentPrice.value}",
                    style = MaterialTheme.typography.body1,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp)
                )

                Text(
                    text = "Last Updated: ${lastUpdated.value}",
                    style = MaterialTheme.typography.body1,
                    fontSize = 15.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp)
                )

                Spacer(modifier = Modifier.height(15.dp))

                LazyColumn {
                    items(historicalData.sortedByDescending { it.date }) { data ->
                        Card(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(),
                            elevation = 0.dp,
                            backgroundColor = colorResource(id = R.color.cream),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "${currencies[selectedCurrencyIndex].toUpperCase(Locale.getDefault())} ${data.price}",
                                    style = MaterialTheme.typography.body1,
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.Black
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = data.date,
                                    style = MaterialTheme.typography.body1,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

        }

        fetchData()
        fetchDatacurrent()
    }

    private fun fetchDatacurrent() {
        lifecycleScope.launch {
            while (true) {
                val currentPriceResponse = fetchCurrentPrice()
                currentPrice.value = currentPriceResponse.price
                lastUpdated.value = currentPriceResponse.lastUpdated
                Log.d("currentprice-->",currentPrice.value.toString())
                delay(60000) // Update every minute (adjust interval as needed)
            }
        }
    }

    private fun fetchData() {
        lifecycleScope.launch {
            val historicalDataResponse = fetchHistoricalData()
            historicalData.clear()
            historicalData.addAll(historicalDataResponse)
        }
    }

    private suspend fun fetchCurrentPrice(): CurrentPriceResponse {
        val request = Request.Builder()
            .url("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=${currencies[selectedCurrencyIndex]}")
            .build()

        val response = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute()
        }

        val json = response.body?.string()
        val price = moshi.adapter(Map::class.java).fromJson(json)?.get("bitcoin") as? Map<*, *>
        val currentPriceValue = price?.get(currencies[selectedCurrencyIndex]) as? Double
        val lastUpdatedValue =
            SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())

        return CurrentPriceResponse(currentPriceValue ?: 0.0, lastUpdatedValue)
    }

    private suspend fun fetchHistoricalData(): List<HistoricalData> {
        val request = Request.Builder()
            .url("https://api.coingecko.com/api/v3/coins/bitcoin/market_chart?vs_currency=${currencies[selectedCurrencyIndex]}&days=14&interval=daily")
            .build()

        val response = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute()
        }

        val json = response.body?.string()

        return try {
            val data = moshi.adapter(HistoricalDataResponse::class.java).fromJson(json)?.prices
                ?: emptyList()

            val historicalDataList = mutableListOf<HistoricalData>()
            data.forEach { entry ->
                val timestamp = (entry[0] as? Double)?.toLong() ?: 0L
                val date = Date(timestamp)
                val dateFormat = SimpleDateFormat("dd-MM-yyyy")
                val formattedDate = dateFormat.format(date)
                val price = entry[1] as? Double
                val formattedPrice = price?.let {
                    String.format("%.2f", it)
                }
                Log.d("price-->",formattedPrice.toString())

                if (price != null) {
                    historicalDataList.add(HistoricalData(formattedDate, formattedPrice.toString()))
                }
            }

            val currentDate = Calendar.getInstance()
            val todayFormatted = SimpleDateFormat("dd-MM-yyyy").format(currentDate.time)
            historicalDataList.removeAll { it.date == todayFormatted }

            historicalDataList
        } catch (e: Exception) {
            Log.d("empty-->",e.message.toString())
            emptyList()
        }
    }

    data class CurrentPriceResponse(val price: Double, val lastUpdated: String)
    data class HistoricalData(val date: String, val price: String)
    data class HistoricalDataResponse(val prices: List<List<Any>>)
}
