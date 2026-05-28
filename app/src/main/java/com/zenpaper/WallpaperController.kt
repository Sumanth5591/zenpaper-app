package com.zenpaper

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object WallpaperController {
    private const val TAG = "WallpaperController"
    
    // List of pre-screened high-quality spiritual slugs from raw index.json
    val spiritualSlugs = listOf(
        "krishna-celestial-dance",
        "krishna-flute-art",
        "krishna-sun-worship",
        "lord-krishna-fire",
        "krishna-divine-red",
        "krishna-eyes",
        "lord-shiva-divine",
        "shiva-on-bull-a",
        "hanuman-watercolor",
        "hanuman-sunset",
        "hanuman-portrait",
        "ram-bow-arrows",
        "jesus-with-flowers",
        "burning-om-symb",
        "om-ocean-sky",
        "sunwukong-prayer",
        "divine-encounte",
        "divine-rider",
        "divine-shadow-lord",
        "angel-light-ascension-01",
        "angel-with-sword-1",
        "angel-with-sword",
        "angel-with-book",
        "dark-angel-gold-halo",
        "snow-angel-anim",
        "fallen-angel-fire",
        "cat-creation-art"
    )

    fun executeWallpaperUpdate(context: Context, category: String, color: String, target: String): String {
        val prefs = context.getSharedPreferences("zenpaper_prefs", Context.MODE_PRIVATE)
        val apiSource = prefs.getString("pref_api_source", "zenpaper") ?: "zenpaper"

        val directImageUrl: String
        val originalUrl: String
        val finalExtension: String
        val finalBytes: ByteArray
        val displayUrl: String

        if (apiSource == "wallhaven") {
            // 1. Fetch from Wallhaven HD API
            Log.d(TAG, "Wallhaven HD API Source selected")
            
            // Map category to search tags
            var query = when (category) {
                "minimal" -> "minimalist"
                "nature" -> "nature"
                "tech" -> "technology+cyberpunk"
                "abstract" -> "abstract"
                "art" -> "digital+art"
                "cars" -> "sports+car"
                "anime" -> "anime"
                "architecture" -> "architecture"
                "spiritual" -> "spiritual+meditation+statue"
                else -> "wallpaper"
            }

            if (color != "all") {
                query += "+$color"
            }

            val urlString = "https://wallhaven.cc/api/v1/search?q=$query&sorting=random&purity=100"
            Log.d(TAG, "Fetching from Wallhaven: $urlString")
            
            val jsonResponse = makeGetRequest(urlString)
            val jsonObject = org.json.JSONObject(jsonResponse)
            val dataArray = jsonObject.getJSONArray("data")
            if (dataArray.length() == 0) {
                throw Exception("No wallpapers found on Wallhaven for query: $query")
            }

            // Pick a random wallpaper from results
            val randomIndex = (0 until dataArray.length()).random()
            val wallpaperObj = dataArray.getJSONObject(randomIndex)
            directImageUrl = wallpaperObj.getString("path")
            originalUrl = wallpaperObj.getString("url")
            finalExtension = directImageUrl.substringAfterLast(".", "jpg")
            displayUrl = originalUrl

            Log.d(TAG, "Wallhaven direct image path: $directImageUrl")
            finalBytes = downloadImageBytes(directImageUrl)

        } else {
            // 2. Fetch from Curated ZenPaper Minimalist API
            Log.d(TAG, "Curated ZenPaper Minimalist API Selected")
            if (category == "spiritual") {
                val randomSlug = spiritualSlugs.random()
                originalUrl = "https://www.wallwidgy.app/wallpapers/$randomSlug"
                displayUrl = originalUrl
                Log.d(TAG, "Spiritual chosen. Local slug selected: $randomSlug")
                
                // Download original high-res image
                val (imageBytes, ext) = downloadHighResImage(randomSlug)
                finalBytes = imageBytes
                finalExtension = ext
            } else {
                var urlString = "https://wallwidgy.vercel.app/api/wallpapers?type=mobile&count=1"
                if (category != "all") {
                    urlString += "&category=$category"
                }
                if (color != "all") {
                    urlString += "&color=$color"
                }

                Log.d(TAG, "Fetching from ZenPaper API: $urlString")
                val jsonResponse = makeGetRequest(urlString)
                val jsonObject = org.json.JSONObject(jsonResponse)
                val wallpapersArray = jsonObject.getJSONArray("wallpapers")
                if (wallpapersArray.length() == 0) {
                    throw Exception("No wallpapers found matching filters")
                }

                originalUrl = wallpapersArray.getString(0)
                displayUrl = originalUrl
                val slug = originalUrl.substringAfterLast("/")
                Log.d(TAG, "Clean API URL: $originalUrl, Slug: $slug")
                
                // Download original high-res image
                val (imageBytes, ext) = downloadHighResImage(slug)
                finalBytes = imageBytes
                finalExtension = ext
            }
        }

        // Apply dynamic aspect ratio crop & scale to perfectly fit mobile screen resolution
        Log.d(TAG, "Processing image bitmap to perfectly fit S25/device screen resolution...")
        val processedBytes = processImageToFitScreen(context, finalBytes, finalExtension)

        // Save high-resolution fitted image to storage
        val slugName = if (apiSource == "wallhaven") "wallhaven_${displayUrl.substringAfterLast("/")}" else displayUrl.substringAfterLast("/")
        val savedUri = saveImageToStorage(context, processedBytes, slugName, finalExtension)
        Log.d(TAG, "Saved high-res fitted image to storage URI: $savedUri")

        // Apply to Wallpaper
        updateWallpaper(context, processedBytes, target)
        Log.d(TAG, "Fitted wallpaper applied successfully")

        // Save to recent list
        addRecentWallpaper(context, displayUrl)
        return displayUrl
    }

    private fun makeGetRequest(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.doInput = true
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun downloadImageBytes(imageUrlString: String): ByteArray {
        val url = URL(imageUrlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.doInput = true
        
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw java.io.IOException("HTTP error code: $responseCode")
        }
        
        return connection.inputStream.use { it.readBytes() }
    }

    private fun downloadHighResImage(slug: String): Pair<ByteArray, String> {
        val extensions = listOf("png", "jpg", "jpeg", "webp")
        for (ext in extensions) {
            val urlString = "https://raw.githubusercontent.com/not-ayan/storage/main/main/$slug.$ext"
            try {
                Log.d(TAG, "Attempting high-res download: $urlString")
                val bytes = downloadImageBytes(urlString)
                if (bytes.isNotEmpty()) {
                    Log.d(TAG, "Successfully downloaded high-res image: $urlString")
                    return Pair(bytes, ext)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed download for $urlString: ${e.message}")
            }
        }
        
        // Fallback to cache webp
        val fallbackUrl = "https://raw.githubusercontent.com/not-ayan/storage/main/cache/$slug.webp"
        Log.d(TAG, "Falling back to cache webp: $fallbackUrl")
        return Pair(downloadImageBytes(fallbackUrl), "webp")
    }

    private fun processImageToFitScreen(context: Context, imageBytes: ByteArray, extension: String): ByteArray {
        return try {
            val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return imageBytes
            val fittedBitmap = cropAndScaleBitmapToFitScreen(context, originalBitmap)
            
            val outputStream = ByteArrayOutputStream()
            val format = if (extension.lowercase() == "png") Bitmap.CompressFormat.PNG 
                         else if (extension.lowercase() == "webp") Bitmap.CompressFormat.WEBP
                         else Bitmap.CompressFormat.JPEG
                         
            fittedBitmap.compress(format, 95, outputStream)
            
            // Clean up resources immediately to save S25 battery and memory
            originalBitmap.recycle()
            fittedBitmap.recycle()
            
            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping/scaling image, falling back to original", e)
            imageBytes
        }
    }

    private fun cropAndScaleBitmapToFitScreen(context: Context, originalBitmap: Bitmap): Bitmap {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
        val bitmapWidth = originalBitmap.width
        val bitmapHeight = originalBitmap.height
        val bitmapAspectRatio = bitmapWidth.toFloat() / bitmapHeight.toFloat()
        
        var cropWidth = bitmapWidth
        var cropHeight = bitmapHeight
        var startX = 0
        var startY = 0
        
        if (bitmapAspectRatio > screenAspectRatio) {
            // Original is wider than screen aspect ratio -> crop width
            cropWidth = (bitmapHeight * screenAspectRatio).toInt()
            startX = (bitmapWidth - cropWidth) / 2
        } else {
            // Original is taller than screen aspect ratio -> crop height
            cropHeight = (bitmapWidth / screenAspectRatio).toInt()
            startY = (bitmapHeight - cropHeight) / 2
        }
        
        Log.d(TAG, "Screen Size: ${screenWidth}x${screenHeight}, Image Size: ${bitmapWidth}x${bitmapHeight}, Cropping to: Start(${startX}, ${startY}), Size(${cropWidth}x${cropHeight})")
        
        val croppedBitmap = Bitmap.createBitmap(originalBitmap, startX, startY, cropWidth, cropHeight)
        val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, screenWidth, screenHeight, true)
        
        if (croppedBitmap != originalBitmap) {
            croppedBitmap.recycle()
        }
        
        return scaledBitmap
    }

    private fun saveImageToStorage(context: Context, imageBytes: ByteArray, slug: String, extension: String): Uri? {
        val filename = "zenpaper_${slug}_${System.currentTimeMillis()}.$extension"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mimeType = when (extension.lowercase()) {
                "webp" -> "image/webp"
                "png" -> "image/png"
                else -> "image/jpeg"
            }
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ZenPaper")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(imageBytes)
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            return uri
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val zenDir = File(picturesDir, "ZenPaper")
            if (!zenDir.exists()) {
                zenDir.mkdirs()
            }
            val file = File(zenDir, filename)
            file.writeBytes(imageBytes)
            
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            return Uri.fromFile(file)
        }
    }

    private fun updateWallpaper(context: Context, imageBytes: ByteArray, target: String) {
        val wallpaperManager = WallpaperManager.getInstance(context)
        
        if (target == "home" || target == "both") {
            val stream = ByteArrayInputStream(imageBytes)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setStream(stream, null, true, WallpaperManager.FLAG_SYSTEM)
            } else {
                wallpaperManager.setStream(stream)
            }
        }
        if (target == "lock" || target == "both") {
            val stream = ByteArrayInputStream(imageBytes)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    wallpaperManager.setStream(stream, null, true, WallpaperManager.FLAG_LOCK)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set lock screen wallpaper", e)
                }
            }
        }
    }

    private fun addRecentWallpaper(context: Context, url: String) {
        val prefs = context.getSharedPreferences("zenpaper_prefs", Context.MODE_PRIVATE)
        val recents = prefs.getString("pref_recents", "") ?: ""
        val list = recents.split(",").filter { it.isNotEmpty() }.toMutableList()
        
        if (!list.contains(url)) {
            list.add(0, url)
            if (list.size > 10) {
                list.removeAt(list.size - 1)
            }
            prefs.edit().putString("pref_recents", list.joinToString(",")).apply()
        }
    }
}
