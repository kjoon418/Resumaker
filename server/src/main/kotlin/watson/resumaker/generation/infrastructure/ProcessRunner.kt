package watson.resumaker.generation.infrastructure

import java.time.Duration

/**
 * 외부 프로세스 실행 seam(구현 설계 §10, §12 "AI 공급자=포트로 교체 가능").
 *
 * Claude CLI를 셸로 호출하는 [ClaudeCliClient]가 실제 프로세스 실행을 이 인터페이스 뒤로 격리한다.
 * 그래야 단위 테스트가 **실제 `claude` CLI를 호출하지 않고** fake 구현(canned 결과)을 주입할 수 있다
 * (비용 0·결정성 확보). 프로덕션 구현은 [ProcessBuilderProcessRunner](ProcessBuilder 기반)이다.
 *
 * @param command 실행할 명령과 인자(첫 원소가 실행 파일, 이후가 인자).
 * @param stdin   프로세스 표준입력으로 흘려보낼 텍스트(프롬프트). 비어 있으면 입력을 닫는다.
 * @param timeout 이 시간을 초과하면 [ProcessTimeoutException]을 던지고 프로세스를 강제 종료한다.
 */
interface ProcessRunner {
    fun run(command: List<String>, stdin: String, timeout: Duration): ProcessResult
}

/**
 * 프로세스 실행 결과(종료코드·표준출력·표준오류).
 * 비정상 종료(exitCode != 0)나 비정상 출력의 해석은 호출자([ClaudeCliClient])가 방어적으로 판정한다.
 */
data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/**
 * 프로세스가 타임아웃 안에 끝나지 않은 경우. [ClaudeCliClient]가 명확한 실패로 전파한다.
 */
class ProcessTimeoutException(message: String) : RuntimeException(message)

/**
 * 프로세스를 시작조차 하지 못한 경우(예: 실행 파일 없음). [ClaudeCliClient]가 명확한 실패로 전파한다.
 */
class ProcessExecutionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
