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
    /**
     * 리더 스레드 [Thread.join] 대기 상한(ms). 기본값은 프로덕션 값([DEFAULT_readerJoinTimeoutMillis]).
     * 테스트에서 짧은 값(예: 200ms)을 주입하면 "EOF 없이도 join이 cap 이내에 반환"하는지 빠르게 단언할 수 있다(결함3).
     */
    private val readerJoinTimeoutMillis: Long = DEFAULT_readerJoinTimeoutMillis,
) : ProcessRunner {

    companion object {
        /**
         * 리더 스레드 [Thread.join] 대기 상한 기본값(ms). 정상 종료 경로에서는 프로세스가 이미 exit해 파이프가 곧
         * EOF가 되므로 이 상한 안에 결과 수집이 끝난다(결과 유실 없음). 타임아웃·강제 종료 경로에서는 orphan(손자
         * 프로세스)이 파이프를 잡고 있어도 데몬 리더가 이 상한까지만 기다린 뒤 요청 스레드가 진행하도록 보장한다
         * (영구 블록 방지, 결함3). 5초는 정상 EOF 도달에는 충분히 넉넉하고, 비정상 경로에서 요청 스레드를 과도하게
         * 잡아두지 않는 값이다. 테스트에서는 생성자 파라미터로 짧게 주입한다.
         */
        const val DEFAULT_readerJoinTimeoutMillis = 5_000L

        /**
         * 해석된 명령의 **인자(인덱스 1 이후)** 만 Windows 인용 규칙으로 이스케이프한 새 리스트를 반환한다(결함4).
         *
         * **왜 필요한가:** `ClaudeCliClient`는 `--json-schema <JSON>`처럼 큰따옴표가 가득한 JSON 문자열을 인자로 넘긴다.
         * JVM [ProcessBuilder]가 이 인자를 Windows 명령행으로 변환할 때 Java의 인용 로직과 대상 바이너리(claude.exe →
         * `CommandLineToArgvW` 파서)의 파싱이 불일치해 JSON의 모든 `"`가 소실된다(`{"type":"object"}` → `{type:object}`).
         * 무효 JSON을 받은 claude.exe는 출력 한 줄 없이 무한 행하다 타임아웃된다.
         *
         * **해법:** 각 인자를 정규 Windows 인용 규칙(소위 "Daniel Colascione / MSVCRT" 알고리즘)으로 미리 `"..."` 토큰으로
         * 인용하면, Java가 이를 "이미 인용됨"으로 인식해 그대로 통과시키고 대상 파서가 `\"`→`"`로 복원한다(실증됨).
         *
         * - 실행 파일(인덱스 0)은 Java/[CommandResolver]가 처리하므로 **변형하지 않는다**.
         * - `isWindows`가 false(Linux/프로덕션 docker)면 원본을 그대로 반환한다(인자 절대 무변형).
         */
        fun escapeArgumentsForWindows(command: List<String>, isWindows: Boolean): List<String> {
            if (!isWindows || command.size <= 1) return command
            return listOf(command[0]) + command.drop(1).map { quoteWindowsArgument(it) }
        }

        /**
         * 단일 인자를 정규 Windows 인용 규칙으로 인용한다(순수 함수: 문자열 in → 문자열 out).
         *
         * 공백·탭·`"`·줄바꿈 등 특수문자가 없으면 **그대로 둔다**(불필요한 인용 금지, 멱등).
         * 그 외에는 큰따옴표로 감싸되:
         * - `"` 앞에 쌓인 연속 백슬래시 런은 2배로 만든 뒤 `\"`로 escape한다.
         * - 닫는 따옴표 직전의 trailing 백슬래시 런도 2배로 만든다(이 단계 없이 단순 치환만 하면 trailing 백슬래시가
         *   닫는 따옴표를 escape해버려 파싱이 깨진다 — 이게 단순 `replace`가 틀리는 엣지케이스다).
         */
        fun quoteWindowsArgument(argument: String): String {
            if (argument.isNotEmpty() && argument.none { it == ' ' || it == '\t' || it == '"' || it == '\n' || it == '\r' }) {
                return argument
            }

            val sb = StringBuilder()
            sb.append('"')
            var backslashCount = 0
            for (ch in argument) {
                when (ch) {
                    '\\' -> backslashCount++
                    '"' -> {
                        // `"` 앞의 백슬래시 런을 2배로 만든 뒤(리터럴 백슬래시 보존) `"`를 escape한다.
                        sb.append("\\".repeat(backslashCount * 2 + 1))
                        sb.append('"')
                        backslashCount = 0
                    }
                    else -> {
                        sb.append("\\".repeat(backslashCount))
                        sb.append(ch)
                        backslashCount = 0
                    }
                }
            }
            // 닫는 따옴표 직전의 trailing 백슬래시 런을 2배로(닫는 `"`를 escape하지 않도록).
            sb.append("\\".repeat(backslashCount * 2))
            sb.append('"')
            return sb.toString()
        }
    }

    override fun run(command: List<String>, stdin: String, timeout: Duration): ProcessResult {
        // 실행 직전 OS에 맞게 실행 파일(command[0])을 해석한다(해석 실패 시 원본 그대로).
        val resolvedCommand = commandResolver.resolve(command)

        // Windows에서만 인자를 정규 Windows 인용 규칙으로 이스케이프한다(결함4).
        // Linux/프로덕션 docker는 ProcessBuilder가 execvp로 인자를 verbatim 전달하므로 절대 변형하면 안 된다.
        val launchCommand = escapeArgumentsForWindows(resolvedCommand, commandResolver.isWindows)

        val process = try {
            ProcessBuilder(launchCommand)
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
            // 프로세스가 정상 exit했으므로 파이프는 곧 EOF가 되고, 데몬 리더는 타임아웃 안에 결과 수집을 끝낸다.
            stdoutReader.join(readerJoinTimeoutMillis)
            stderrReader.join(readerJoinTimeoutMillis)
            return ProcessResult(
                exitCode = process.exitValue(),
                stdout = stdoutReader.result(),
                stderr = stderrReader.result(),
            )
        } finally {
            // 살아 있으면 프로세스 트리 전체를 강제 종료한다. 직계 자식(Windows의 claude.cmd)만 죽이면
            // 손자(claude.exe)가 살아남아 stdout 파이프를 잡고 있어 리더가 EOF를 영영 못 받는다(결함3).
            // descendants()는 destroy 호출 후 비므로 **죽이기 전에 스냅샷**으로 수집해 자식 → 손자 순으로 종료한다.
            if (process.isAlive) {
                val descendants = process.descendants().toList()
                process.destroyForcibly()
                descendants.forEach { it.destroyForcibly() }
            }
            // 데몬 리더는 타임아웃까지만 기다린 뒤 요청 스레드를 진행시킨다. orphan이 파이프를 잡고 있어도
            // 리더는 데몬이라 JVM 종료를 막지 않으며, 요청 스레드는 여기서 영구 블록되지 않는다(결함3).
            stdoutReader.join(readerJoinTimeoutMillis)
            stderrReader.join(readerJoinTimeoutMillis)
        }
    }

    private fun readStreamAsync(stream: java.io.InputStream): StreamReaderThread {
        val thread = StreamReaderThread(stream)
        thread.start()
        return thread
    }

    private class StreamReaderThread(private val stream: java.io.InputStream) : Thread() {
        init {
            // 데몬 스레드: orphan(손자 프로세스)이 파이프를 잡아 리더가 EOF를 못 받고 살아남아도
            // JVM 종료를 막지 않게 한다(결함3). join 타임아웃과 함께 요청 스레드 영구 블록을 차단한다.
            isDaemon = true
        }

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
    /**
     * 현재 OS가 Windows인지 여부. 실행 파일 해석 분기뿐 아니라 Windows 전용 인자 이스케이프(결함4) 분기에서도
     * 동일 seam을 재사용하도록 외부에서 읽을 수 있게 공개한다(실제 OS 비의존 단위 테스트 가능).
     */
    val isWindows: Boolean,
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
