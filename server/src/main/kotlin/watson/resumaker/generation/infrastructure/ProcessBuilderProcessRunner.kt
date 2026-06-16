package watson.resumaker.generation.infrastructure

import org.springframework.stereotype.Component
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * [ProcessRunner]의 프로덕션 구현. JVM [ProcessBuilder]로 외부 명령을 실행하고 stdin/stdout/stderr를 다룬다.
 *
 * stdout/stderr는 데드락을 피하기 위해 별도 스레드로 동시에 읽어 들인다(파이프 버퍼가 차서 자식이 블록되는 것을 방지).
 * 타임아웃 초과 시 프로세스를 강제 종료하고 [ProcessTimeoutException]을 던진다. 실행 실패(파일 없음 등)는
 * [ProcessExecutionException]으로 감싼다.
 */
@Component
class ProcessBuilderProcessRunner : ProcessRunner {

    override fun run(command: List<String>, stdin: String, timeout: Duration): ProcessResult {
        val process = try {
            ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()
        } catch (e: IOException) {
            throw ProcessExecutionException("외부 프로세스를 시작하지 못했어요.", e)
        }

        // stdout/stderr를 동시에 읽어 파이프 버퍼 포화로 인한 데드락을 방지한다.
        val stdoutReader = readStreamAsync(process.inputStream)
        val stderrReader = readStreamAsync(process.errorStream)

        // 모든 종료 경로(정상·타임아웃·IOException·OOM 등 비검사 예외)에서 리더 스레드 join과
        // 자식 프로세스 정리를 보장한다(스레드/프로세스 누수 방지).
        try {
            try {
                process.outputStream.use { output ->
                    if (stdin.isNotEmpty()) {
                        output.write(stdin.toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                }
            } catch (e: IOException) {
                // 프로세스가 이미 입력을 닫았을 수 있다(정상). 종료 코드로 판정한다.
            }

            val finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
            if (!finished) {
                throw ProcessTimeoutException("외부 프로세스가 제한 시간(${timeout.toSeconds()}초) 안에 끝나지 않았어요.")
            }

            // 리더 스레드를 먼저 join해 stdout/stderr를 완전히 수집한 뒤 결과를 읽는다.
            stdoutReader.join()
            stderrReader.join()
            return ProcessResult(
                exitCode = process.exitValue(),
                stdout = stdoutReader.result(),
                stderr = stderrReader.result(),
            )
        } finally {
            // 살아 있으면 강제 종료해 자식 프로세스가 남지 않게 하고, 리더 스레드는 항상 join한다.
            // (정상 경로는 위에서 이미 join했으므로 멱등하게 다시 join해도 안전하다.)
            if (process.isAlive) {
                process.destroyForcibly()
            }
            stdoutReader.join()
            stderrReader.join()
        }
    }

    private fun readStreamAsync(stream: java.io.InputStream): StreamReaderThread {
        val thread = StreamReaderThread(stream)
        thread.start()
        return thread
    }

    private class StreamReaderThread(private val stream: java.io.InputStream) : Thread() {
        @Volatile
        private var collected: String = ""

        override fun run() {
            collected = stream.readBytes().toString(Charsets.UTF_8)
        }

        fun result(): String = collected
    }
}
