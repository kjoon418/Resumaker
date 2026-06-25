package watson.resumaker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ResumakerApplication

fun main(args: Array<String>) {
    runApplication<ResumakerApplication>(*args)
}
