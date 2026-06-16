package watson.resumaker.generation.infrastructure

import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * [ProcessRunner]의 프로덕션 구현. JVM [ProcessBuilder]로 외부 명령을 실행하고 stdin/stdout/stderr를 다룬다.
 *
 * stdout/stderr는 데드락을 피하기 위해 별도 스레드로 동시에 읽어 들인다(파이프 버퍼가 차서 자식이 블록되는 것을 방지).
 * 타임아웃 초과 시 프로세스를 강제 종료하고 [ProcessTimeoutException]을 던진다. 실행 실패(파일 없음 등)는
 * [ProcessExecutionException]으로 감싼다.
 *
 * **Windows 실행 파일 해석(결함2 수정):** npm 전역 shim은 `claude.cmd`이고 확장자 없는 `claude`는 bash 스크립트라
 * JVM이 직접 실행하지 못한다(`CreateProcess error=2`). 그래서 Windows에서는 실행 직전 `command[0]`이 bare name
 * (경로 구분자·확장자 없음)일 때 `PATH` 디렉터리 × `PATHEXT` 확장자 조합을 스캔해 **존재하는 첫 파일의 절대경로**로
 * 치환한다(검증된 해법: 풀패스 `.cmd` 직접 실행). 이미 경로/확장자가 있으면 그대로 두고, 후보가 없으면 원본을 그대로
 * 둬서 기존 [ProcessExecutionException] 경로가 동작하게 한다. Windows가 아니면(Linux/프로덕션 docker) 아무것도
 * 바꾸지 않는다(PATH의 `claude`를 그대로 실행).
 *
 * OS 감지·`PATH`/`PATHEXT` 조회·파일 존재 확인은 [CommandResolver] seam으로 분리해 실제 파일시스템/실제 OS 없이도
 * 명령 해석 분기를 단위 테스트할 수 있게 한다. 기본 생성자는 프로덕션 기본값([CommandResolver.production])을 쓰고,
 * 테스트는 fake seam을 주입한다.
 */
@Component
class ProcessBuilderProcessRunner(
    private val commandResolver: CommandResolver = CommandResolver.production(),
) : ProcessRunner {

    override fun run(command: List<String>, stdin: String, timeout: Duration): ProcessResult {
        // 실행 직전 OS에 맞게 실행 파일(command[0])을 해석한다(해석 실패 시 원본 그대로).
        val resolvedCommand = commandResolver.resolve(command)

        val process = try {
            ProcessBuilder(resolvedCommand)
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

/**
 * 실행 직전 명령의 실행 파일(`command[0]`)을 OS에 맞게 해석하는 seam.
 *
 * OS 감지·`PATH`/`PATHEXT` 조회·파일 존재 확인을 주입 가능한 함수로 분리해, 실제 파일시스템이나 실제 OS에 의존하지
 * 않고 단위 테스트가 모든 분기(Windows bare name 치환 / 이미 경로·확장자 있음 / 후보 없음 / non-Windows 무변경)를
 * 검증할 수 있게 한다.
 *
 * @param isWindows   현재 OS가 Windows인지 여부.
 * @param pathDirs    `PATH`에서 추출한 디렉터리 목록(앞에서부터 우선 탐색).
 * @param pathExts    `PATHEXT` 확장자 목록(예: `.COM`, `.EXE`, `.BAT`, `.CMD`; 앞에서부터 우선 매칭).
 * @param fileExists  주어진 절대경로에 실제 파일이 존재하는지 확인하는 술어(파일시스템 seam).
 */
class CommandResolver(
    private val isWindows: Boolean,
    private val pathDirs: List<String>,
    private val pathExts: List<String>,
    private val fileExists: (String) -> Boolean,
) {

    /**
     * `command`의 실행 파일을 OS에 맞게 해석한 새 명령을 반환한다.
     *
     * Windows이고 `command[0]`이 bare name(경로 구분자·확장자 없음)이면 `PATH` × `PATHEXT` 조합으로 스캔해
     * **존재하는 첫 파일의 절대경로**로 실행 파일을 치환한다. 이미 경로/확장자가 있거나 후보가 없으면, 혹은 Windows가
     * 아니면 명령을 변경하지 않고 그대로 반환한다(후보 없음은 원본 유지 → 기존 [ProcessExecutionException] 경로 동작).
     */
    fun resolve(command: List<String>): List<String> {
        if (!isWindows || command.isEmpty()) return command

        val executable = command[0]
        if (!isBareName(executable)) return command

        val resolved = findOnPath(executable) ?: return command
        return listOf(resolved) + command.drop(1)
    }

    /** 경로 구분자(`/`, `\`)와 확장자(`.`)가 모두 없으면 bare name으로 본다(PATH 탐색 대상). */
    private fun isBareName(executable: String): Boolean {
        if (executable.contains('/') || executable.contains('\\')) return false
        return !executable.contains('.')
    }

    /** `PATH` 디렉터리 × `PATHEXT` 확장자 순서대로 첫 번째로 존재하는 파일의 절대경로를 찾는다(없으면 null). */
    private fun findOnPath(bareName: String): String? {
        for (dir in pathDirs) {
            if (dir.isBlank()) continue
            for (ext in pathExts) {
                val candidate = File(dir, bareName + ext).absolutePath
                if (fileExists(candidate)) return candidate
            }
        }
        return null
    }

    companion object {
        /**
         * 프로덕션 기본값으로 [CommandResolver]를 만든다.
         * 실제 `os.name`·`PATH`·`PATHEXT` 환경과 실제 파일 존재 확인을 사용한다.
         */
        fun production(): CommandResolver {
            val osName = System.getProperty("os.name").orEmpty()
            val isWindows = osName.lowercase().contains("win")
            val pathDirs = System.getenv("PATH").orEmpty()
                .split(File.pathSeparatorChar)
                .filter { it.isNotBlank() }
            val pathExts = System.getenv("PATHEXT").orEmpty()
                .split(File.pathSeparatorChar)
                .filter { it.isNotBlank() }
            return CommandResolver(
                isWindows = isWindows,
                pathDirs = pathDirs,
                pathExts = pathExts,
                fileExists = { path -> File(path).isFile },
            )
        }
    }
}
