package org.qosp.notes.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.qosp.notes.R

private const val TotalPages = 2

@Composable
fun WelcomeScreen(onNextClicked: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { TotalPages })

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> WelcomeContent { WhatIsNew() }
                    1 -> FinalPage(onNextClicked)
                }
            }
            DotsIndicator(totalDots = TotalPages, selectedIndex = pagerState.currentPage)
        }
    }
}

@Composable
fun WelcomeContent(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}

@Composable
fun WhatIsNew() {
    val context = LocalContext.current
    Text(
        text = context.getString(R.string.what_is_new),
        modifier = Modifier.padding(bottom = 16.dp),
        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp)
    )
    val lines = context.getString(R.string.what_is_new_content).split("\n")
        .map { it.trim() }.filter { it.isNotBlank() }
    LazyColumn {
        items(lines) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Absolute.Left) {
                Text(text = "🟢", modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp), fontSize = 15.sp)
                Text(text = it, modifier = Modifier.padding(8.dp))
            }
        }
    }
}


@Composable
fun FinalPage(onNextClicked: () -> Unit) {
    LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = "Enjoy the app!",
                modifier = Modifier.padding(bottom = 16.dp),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Button(onClick = onNextClicked) { Text(stringResource(id = R.string.continue_next)) }
        }

    }
}

@Composable
fun DotsIndicator(totalDots: Int, selectedIndex: Int) {
    Row(
        modifier = Modifier
            .padding(bottom = 24.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalArrangement = Arrangement.Center
    ) {
        for (i in 0 until totalDots) {
            val color = if (i == selectedIndex) MaterialTheme.colorScheme.primary else Color.Gray
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .clip(CircleShape)
                    .size(10.dp)
                    .background(color)
            )
        }
    }
}
