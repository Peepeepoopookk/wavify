package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun D3WaveformVisualizer(
    trackId: String,
    playbackPosition: Long,
    playbackDuration: Long,
    isPlaying: Boolean,
    accentColor: Color,
    baseColor: Color = Color(0xFFE5E5EA),
    modifier: Modifier = Modifier
) {
    // Convert colors to HEX representation
    val hexAccent = remember(accentColor) { accentColor.toHex() }
    val hexBase = remember(baseColor) { baseColor.toHex() }

    // Calculate progress as fractional float
    val progress = remember(playbackPosition, playbackDuration) {
        if (playbackDuration > 0) {
            (playbackPosition.toFloat() / playbackDuration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    // Hold reference to WebView
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    // Launch evaluation when state changes
    LaunchedEffect(trackId, progress, isPlaying, hexAccent, hexBase, webViewRef, isLoaded) {
        val webView = webViewRef
        if (webView != null && isLoaded) {
            val js = "updateVisualizer('$trackId', $progress, $isPlaying, '$hexAccent', '$hexBase');"
            webView.evaluateJavascript(js, null)
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                
                // Keep the WebView container background transparent to match design scheme
                setBackgroundColor(0)
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        isLoaded = true
                        // Run initial state update immediately on page load
                        val js = "updateVisualizer('$trackId', $progress, $isPlaying, '$hexAccent', '$hexBase');"
                        view?.evaluateJavascript(js, null)
                    }
                }
                
                loadDataWithBaseURL("https://localhost", htmlContent, "text/html", "UTF-8", null)
                webViewRef = this
            }
        },
        update = { webView ->
            webViewRef = webView
        },
        modifier = modifier
    )
}

private fun Color.toHex(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return String.format("#%02x%02x%02x", r, g, b)
}

private val htmlContent = """
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <style>
        body, html {
            margin: 0;
            padding: 0;
            width: 100%;
            height: 100%;
            overflow: hidden;
            background-color: transparent;
        }
        svg {
            width: 100%;
            height: 100%;
            display: block;
        }
    </style>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/d3/7.8.5/d3.min.js"></script>
</head>
<body>
    <svg id="waveform"></svg>
    <script>
        let latestTrackId = "";
        let progress = 0;
        let isPlaying = false;
        let accentColor = "#007AFF";
        let baseColor = "#E5E5EA";
        let heights = [];
        let animationFrameId = null;
        
        let width = window.innerWidth || document.documentElement.clientWidth;
        let height = window.innerHeight || document.documentElement.clientHeight;

        function seedRandom(seedStr) {
            let hash = 0;
            for (let i = 0; i < seedStr.length; i++) {
                hash = seedStr.charCodeAt(i) + ((hash << 5) - hash);
            }
            return function() {
                const x = Math.sin(hash++) * 10000;
                return x - Math.floor(x);
            };
        }

        function generateHeights(trackId, count) {
            const rand = seedRandom(trackId || "default");
            const arr = [];
            for (let i = 0; i < count; i++) {
                // Generate a wave template: larger in the middle, smaller at edges
                const t = i / (count - 1);
                const bell = Math.sin(t * Math.PI);
                const noise = 0.25 + 0.75 * rand();
                const factor = Math.max(0.18, bell * noise);
                arr.push(factor);
            }
            return arr;
        }

        function render() {
            width = window.innerWidth || document.documentElement.clientWidth;
            height = window.innerHeight || document.documentElement.clientHeight;
            
            const barCount = 45;
            if (heights.length !== barCount) {
                heights = generateHeights(latestTrackId, barCount);
            }

            const padding = 2;
            const barWidth = (width / barCount) - padding;

            if (typeof d3 !== 'undefined') {
                const svg = d3.select("#waveform");
                svg.attr("viewBox", "0 0 " + width + " " + height);
                const bars = svg.selectAll("rect").data(heights, (d, i) => i);
                
                bars.enter()
                    .append("rect")
                    .merge(bars)
                    .attr("x", (d, i) => i * (barWidth + padding) + padding/2)
                    .attr("width", barWidth)
                    .attr("rx", barWidth / 2)
                    .attr("ry", barWidth / 2)
                    .attr("y", d => {
                        const h = d * (height - 4);
                        return (height - h) / 2;
                    })
                    .attr("height", d => d * (height - 4))
                    .attr("fill", (d, i) => {
                        const barProgress = i / barCount;
                        return barProgress <= progress ? accentColor : baseColor;
                    });
            } else {
                // Direct vanilla SVG render fallback if D3 stream is missing/loading
                const waveformSvg = document.getElementById("waveform");
                if (waveformSvg) {
                    waveformSvg.setAttribute("viewBox", "0 0 " + width + " " + height);
                    let innerHtml = "";
                    for (let i = 0; i < barCount; i++) {
                        const d = heights[i];
                        const barProgress = i / barCount;
                        const fill = barProgress <= progress ? accentColor : baseColor;
                        const h = d * (height - 4);
                        const y = (height - h) / 2;
                        const x = i * (barWidth + padding) + padding/2;
                        innerHtml += '<rect x="' + x + '" y="' + y + '" width="' + barWidth + '" height="' + h + '" rx="' + (barWidth/2) + '" ry="' + (barWidth/2) + '" fill="' + fill + '"></rect>';
                    }
                    waveformSvg.innerHTML = innerHtml;
                }
            }
        }

        window.addEventListener("resize", render);

        function updateVisualizer(trackId, newProgress, playing, hexAccent, hexBase) {
            let trackChanged = false;
            if (trackId !== latestTrackId) {
                latestTrackId = trackId;
                heights = generateHeights(trackId, 45);
                trackChanged = true;
            }
            progress = newProgress;
            isPlaying = playing;
            accentColor = hexAccent;
            baseColor = hexBase;

            if (trackChanged) {
                render();
            }

            if (isPlaying) {
                if (!animationFrameId) {
                    animate();
                }
            } else {
                if (animationFrameId) {
                    cancelAnimationFrame(animationFrameId);
                    animationFrameId = null;
                }
                render();
            }
        }

        function animate() {
            if (!isPlaying) {
                animationFrameId = null;
                return;
            }

            const barCount = heights.length;
            const time = Date.now() * 0.007;

            if (typeof d3 !== 'undefined') {
                const svg = d3.select("#waveform");
                svg.selectAll("rect")
                    .attr("y", (d, i) => {
                        const barProgress = i / barCount;
                        let pulse = 0;
                        if (barProgress <= progress) {
                            pulse = Math.sin(time + i * 0.3) * 0.12;
                        }
                        const activeHeight = Math.max(0.1, d * (1 + pulse)) * (height - 4);
                        return (height - activeHeight) / 2;
                    })
                    .attr("height", (d, i) => {
                        const barProgress = i / barCount;
                        let pulse = 0;
                        if (barProgress <= progress) {
                            pulse = Math.sin(time + i * 0.3) * 0.12;
                        }
                        return Math.max(0.1, d * (1 + pulse)) * (height - 4);
                    })
                    .attr("fill", (d, i) => {
                        const barProgress = i / barCount;
                        return barProgress <= progress ? accentColor : baseColor;
                    });
            } else {
                // Vanilla pulsing updates
                const waveformSvg = document.getElementById("waveform");
                if (waveformSvg) {
                    const rects = waveformSvg.querySelectorAll("rect");
                    if (rects.length === barCount) {
                        for (let i = 0; i < barCount; i++) {
                            const d = heights[i];
                            const rect = rects[i];
                            const barProgress = i / barCount;
                            let pulse = 0;
                            if (barProgress <= progress) {
                                pulse = Math.sin(time + i * 0.3) * 0.12;
                            }
                            const h = Math.max(0.1, d * (1 + pulse)) * (height - 4);
                            const y = (height - h) / 2;
                            rect.setAttribute("y", y);
                            rect.setAttribute("height", h);
                            rect.setAttribute("fill", barProgress <= progress ? accentColor : baseColor);
                        }
                    }
                }
            }

            animationFrameId = requestAnimationFrame(animate);
        }
    </script>
</body>
</html>
""".trimIndent()
