package watson.resumaker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// @EnableScheduling: 비동기 생성 작업 워커(GenerationJobWorker)의 @Scheduled 폴링을 활성화한다.
@SpringBootApplication
@EnableScheduling
class ResumakerApplication

fun main(args: Array<String>) {
    runApplication<ResumakerApplication>(*args)
}
