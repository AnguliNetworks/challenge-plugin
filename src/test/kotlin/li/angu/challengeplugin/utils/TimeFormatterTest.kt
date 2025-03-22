package li.angu.challengeplugin.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import java.time.Instant
import java.time.temporal.ChronoUnit

class TimeFormatterTest {

    @Test
    fun `test format duration with only seconds`() {
        val now = Instant.now()
        val start = now.minus(45, ChronoUnit.SECONDS)
        
        val formatted = TimeFormatter.formatDuration(start, now)
        assertEquals("45s", formatted)
    }
    
    @Test
    fun `test format duration with minutes and seconds`() {
        val now = Instant.now()
        val start = now.minus(5, ChronoUnit.MINUTES).minus(15, ChronoUnit.SECONDS)
        
        val formatted = TimeFormatter.formatDuration(start, now)
        assertEquals("5m 15s", formatted)
    }
    
    @Test
    fun `test format duration with hours, minutes and seconds`() {
        val now = Instant.now()
        val start = now.minus(2, ChronoUnit.HOURS)
                       .minus(30, ChronoUnit.MINUTES)
                       .minus(45, ChronoUnit.SECONDS)
        
        val formatted = TimeFormatter.formatDuration(start, now)
        assertEquals("2h 30m 45s", formatted)
    }
    
    @Test
    fun `test format duration with days, hours, minutes and seconds`() {
        val now = Instant.now()
        val start = now.minus(3, ChronoUnit.DAYS)
                       .minus(5, ChronoUnit.HOURS)
                       .minus(12, ChronoUnit.MINUTES)
                       .minus(30, ChronoUnit.SECONDS)
        
        val formatted = TimeFormatter.formatDuration(start, now)
        assertEquals("3d 5h 12m 30s", formatted)
    }
    
    @Test
    fun `test format duration with zero values`() {
        val now = Instant.now()
        val start = now.minus(1, ChronoUnit.DAYS)
                       .minus(0, ChronoUnit.HOURS)
                       .minus(0, ChronoUnit.MINUTES)
                       .minus(30, ChronoUnit.SECONDS)
        
        val formatted = TimeFormatter.formatDuration(start, now)
        assertEquals("1d 0h 0m 30s", formatted)
    }
}