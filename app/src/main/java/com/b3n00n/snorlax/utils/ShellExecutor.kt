package com.b3n00n.snorlax.utils

import java.io.BufferedReader
import java.io.InputStreamReader

object ShellExecutor {
    @Throws(Exception::class)
    fun execute(command: String): String {
        val process = Runtime.getRuntime().exec(command)

        val output = StringBuilder()

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.lineSequence().forEach { line ->
                output.append(line).append("\n")
            }
        }

        BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
            reader.lineSequence().forEach { line ->
                output.append("ERROR: ").append(line).append("\n")
            }
        }

        process.waitFor()
        return output.toString().trim()
    }
}