package watson.resumaker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ResumakerApplication

fun main(args: Array<String>) {
    runApplication<ResumakerApplication>(*args)
}
