package watson.resumaker.generation.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * [CommandResolver] 단위 테스트. **실제 OS·파일시스템·프로세스를 쓰지 않는다** — OS 감지, `PATH`/`PATHEXT`,
 * 파일 존재 확인을 모두 fake seam으로 주입해 명령 해석 분기만 검증한다(결함2 수정).
 *
 * 검증 분기:
 * 1. Windows + bare name → `PATHEXT` 순서대로 첫 매칭의 풀패스로 치환.
 * 2. Windows지만 이미 확장자/경로가 있음 → 그대로.
 * 3. Windows + 후보 없음 → 원본 그대로(기존 [ProcessExecutionException] 경로가 동작).
 * 4. non-Windows → 무변경.
 */
class CommandResolverTest {

    private val pathDirs = listOf("C:\\Users\\user\\AppData\\Roaming\\npm", "C:\\Windows\\System32")
    private val pathExts = listOf(".COM", ".EXE", ".BAT", ".CMD")

    private fun resolver(
        isWindows: Boolean,
        existing: Set<String> = emptySet(),
    ): CommandResolver = CommandResolver(
        isWindows = isWindows,
        pathDirs = pathDirs,
        pathExts = pathExts,
        fileExists = { path -> path in existing },
    )

    /** 풀패스 후보(절대경로)를 만든다. resolve가 File(dir, name).absolutePath로 만드는 값과 일치시킨다. */
    private fun candidate(dir: String, name: String): String = File(dir, name).absolutePath

    @Test
    fun `Windows에서 bare name은 PATHEXT 순서대로 첫 매칭 풀패스로 치환된다`() {
        // npm 디렉터리에 claude.CMD만 존재(.COM/.EXE/.BAT는 없음).
        val cmdPath = candidate(pathDirs[0], "claude.CMD")
        val resolver = resolver(isWindows = true, existing = setOf(cmdPath))

        val result = resolver.resolve(listOf("claude", "-p", "--model", "m"))

        // command[0]만 풀패스로 치환되고 나머지 인자는 보존된다.
        assertThat(result).containsExactly(cmdPath, "-p", "--model", "m")
    }

    @Test
    fun `Windows에서 여러 확장자가 존재하면 PATHEXT 앞 확장자가 먼저 선택된다`() {
        // .EXE와 .CMD가 모두 존재하면 PATHEXT 순서상 앞선 .EXE가 선택돼야 한다.
        val exePath = candidate(pathDirs[0], "claude.EXE")
        val cmdPath = candidate(pathDirs[0], "claude.CMD")
        val resolver = resolver(isWindows = true, existing = setOf(exePath, cmdPath))

        val result = resolver.resolve(listOf("claude"))

        assertThat(result).containsExactly(exePath)
    }

    @Test
    fun `Windows에서 앞선 PATH 디렉터리가 우선한다`() {
        // 두 디렉터리 모두에 .CMD가 있으면 PATH 앞 디렉터리가 우선한다.
        val firstDir = candidate(pathDirs[0], "claude.CMD")
        val secondDir = candidate(pathDirs[1], "claude.CMD")
        val resolver = resolver(isWindows = true, existing = setOf(firstDir, secondDir))

        val result = resolver.resolve(listOf("claude"))

        assertThat(result).containsExactly(firstDir)
    }

    @Test
    fun `Windows에서 이미 확장자가 있으면 그대로 둔다`() {
        val resolver = resolver(isWindows = true, existing = setOf(candidate(pathDirs[0], "claude.CMD")))

        val result = resolver.resolve(listOf("claude.cmd", "-p"))

        assertThat(result).containsExactly("claude.cmd", "-p")
    }

    @Test
    fun `Windows에서 이미 경로가 포함돼 있으면 그대로 둔다`() {
        val absolute = "C:\\custom\\path\\claude"
        val resolver = resolver(isWindows = true, existing = setOf(candidate(pathDirs[0], "claude.CMD")))

        val result = resolver.resolve(listOf(absolute, "-p"))

        assertThat(result).containsExactly(absolute, "-p")
    }

    @Test
    fun `Windows에서 후보가 없으면 원본을 그대로 둔다`() {
        // 존재하는 파일이 하나도 없음 → 치환하지 않고 원본 유지(기존 실행 실패 경로가 동작).
        val resolver = resolver(isWindows = true, existing = emptySet())

        val result = resolver.resolve(listOf("claude", "-p"))

        assertThat(result).containsExactly("claude", "-p")
    }

    @Test
    fun `non-Windows에서는 bare name이어도 명령을 바꾸지 않는다`() {
        // Linux/프로덕션 docker: PATH의 claude를 그대로 실행해야 한다.
        val resolver = resolver(isWindows = false, existing = setOf(candidate(pathDirs[0], "claude.CMD")))

        val result = resolver.resolve(listOf("claude", "-p", "--model", "m"))

        assertThat(result).containsExactly("claude", "-p", "--model", "m")
    }

    @Test
    fun `빈 명령은 그대로 반환된다`() {
        val resolver = resolver(isWindows = true)

        val result = resolver.resolve(emptyList())

        assertThat(result).isEmpty()
    }
}
