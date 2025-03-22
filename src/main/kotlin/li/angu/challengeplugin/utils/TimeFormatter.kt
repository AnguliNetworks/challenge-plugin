package li.angu.challengeplugin.utils

import java.time.Duration
import java.time.Instant

object TimeFormatter {
    
    fun formatDuration(start: Instant, end: Instant? = null): String {
        val duration = if (end != null) {
            Duration.between(start, end)
        } else {
            Duration.between(start, Instant.now())
        }
        
        return formatDuration(duration)
    }
    
    fun formatDuration(duration: Duration): String {
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        
        val segments = mutableListOf<String>()
        
        if (days > 0) {
            segments.add("${days}d")
        }
        
        if (hours > 0 || days > 0) {
            segments.add("${hours}h")
        }
        
        if (minutes > 0 || hours > 0 || days > 0) {
            segments.add("${minutes}m")
        }
        
        segments.add("${seconds}s")
        
        return segments.joinToString(" ")
    }
}