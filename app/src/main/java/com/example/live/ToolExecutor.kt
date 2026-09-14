package com.example.live

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class ToolExecutor(private val context: Context) {
    companion object {
        private const val TAG = "ToolExecutor"
    }

    data class ToolExecutionResult(
        val success: Boolean,
        val output: String,
        val userFacingMessage: String
    )

    fun execute(name: String, argsJson: String): ToolExecutionResult {
        Log.d(TAG, "Executing tool: $name with args: $argsJson")
        return when (name) {
            "openWebsite" -> openWebsite(argsJson)
            else -> ToolExecutionResult(
                success = false,
                output = "Unknown tool: $name",
                userFacingMessage = "Unsupported action: $name"
            )
        }
    }

    private fun openWebsite(argsJson: String): ToolExecutionResult {
        return try {
            val json = org.json.JSONObject(argsJson)
            var url = json.optString("url", "").trim()
            if (url.isEmpty()) {
                return ToolExecutionResult(
                    success = false,
                    output = "Error: url parameter is missing",
                    userFacingMessage = "Couldn't find the link to open, babe."
                )
            }

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            ToolExecutionResult(
                success = true,
                output = "Successfully opened website $url in browser.",
                userFacingMessage = "Opening $url for you right now!"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open website: ${e.message}", e)
            ToolExecutionResult(
                success = false,
                output = "Failed to launch browser: ${e.message}",
                userFacingMessage = "Oops, couldn't open that site for you."
            )
        }
    }
}
