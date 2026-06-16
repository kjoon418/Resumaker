package watson.resumaker.generation.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Windows 전용 인자 이스케이프(결함4) 단위 테스트. **실제 OS·실제 프로세스를 쓰지 않는다** —
 * [ProcessBuilderProcessRunner.quoteWindowsArgument]는 순수 함수(문자열 in → 문자열 out)이고,
 * [ProcessBuilderProcessRunner.escapeArgumentsForWindows]는 `isWindows` 플래그 주입으로 분기를 검증한다.
 *
 * ## 왜 이 테스트가 결함4 수정을 고정하는가
 * `ClaudeCliClient`가 넘기는 `--json-schema <JSON>`은 큰따옴표가 가득한 문자열이다. JVM ProcessBuilder가
 * 이를 Windows 명령행으로 변환할 때 Java 인용 로직과 claude.exe(CommandLineToArgvW) 파싱이 불일치해 `"`가
 * 전부 소실된다. 미리 정규 Windows 인용 규칙으로 `"..."` 토큰을 만들어 넘기면 Java가 그대로 통과시키고 대상
 * 파서가 복원한다. trailing 백슬래시 엣지케이스는 단순 replace로는 틀리므로 정규 알고리즘으로 구현했다.
 */
class WindowsArgumentEscapeTest {

    // ─────────────────────────────────────────────
    // quoteWindowsArgument: 순수 함수 케이스
    // ─────────────────────────────────────────────

    @Test
    fun `특수문자가 없는 인자는 그대로 둔다(불필요한 인용 금지)`() {
        assertThat(ProcessBuilderProcessRunner.quoteWindowsArgument("-p")).isEqualTo("-p")
        assertThat(ProcessBuilderProcessRunner.quoteWindowsArgument("--output-format")).isEqualTo("--output-format")
        assertThat(ProcessBuilderProcessRunner.quoteWindowsArgument("json")).isEqualTo("json")
    }

    @Test
    fun `이스케이프는 멱등이 아닌 단발이지만 무변형 인자에는 멱등이다`() {
        // 특수문자 없는 인자는 두 번 적용해도 동일(무변형).
        val once = ProcessBuilderProcessRunner.quoteWindowsArgument("plain")
        assertThat(ProcessBuilderProcessRunner.quoteWindowsArgument(once)).isEqualTo("plain")
    }

    @Test
    fun `공백이 포함되면 큰따옴표로 감싼다`() {
        assertThat(ProcessBuilderProcessRunner.quoteWindowsArgument("hello world"))
            .isEqualTo("\"hello world\"")
    }

    @Test
    fun `탭이 포함되면 큰따옴표로 감싼다`() {
        assertThat(ProcessBuilderProcessRunner.quoteWindowsArgument("a\tb"))
            .isEqualTo("\"a\tb\"")
    }

    @Test
    fun `내부 큰따옴표는 백슬래시로 escape하고 전체를 감싼다`() {
        // JSON 스키마의 대표 케이스: {"type":"object"} → "{\"type\":\"object\"}"
        val json = "{\"type\":\"object\"}"
        val expected = "\"{\\\"type\\\":\\\"object\\\"}\""
        assertThat(ProcessBuilderProcessRunner.quoteWindowsArgument(json)).isEqualTo(expected)
    }

    @Test
    fun `큰따옴표만 있는 인자도 escape된다`() {
        // 입력: "  → 출력: "\""  (큰따옴표 하나를 \"로 escape하고 전체를 감싼다)
        assertThat(ProcessBuilderProcessRunner.quoteWindowsArgument("\""))
            .isEqualTo("\"\\\"\"")
    }

    @Test
    fun `내부 백슬래시는 따옴표 앞이 아니면 두배로 만들지 않는다`() {
        // C:\path 는 따옴표가 없고 백슬래시도 인용 종료 직전이 아니므로 그대로 보존된다.
        // 단 공백이 없어 무변형이지만, 공백을 넣어 인용 경로를 강제한다.
        assertThat(ProcessBuilderProcessRunner.quoteWindowsArgument("C:\\a b"))
            .isEqualTo("\"C:\\a b\"")
    }

    @Test
    fun `따옴표 앞의 백슬래시 런은 두배가 된다`() {
        // 입력: a\" (백슬래시 1개 + 따옴표) → "a\\\"" : 백슬래시 1개를 2개로(리터럴 보존), 따옴표를 \"로.
        // 공백이 없으면 무변형이므로 공백을 포함해 인용 경로를 강제한다.
        // 입력: ' a\"' → 백슬래시 1 + 따옴표
        val input = "a\\\"" // a 백슬래시 따옴표 (특수문자 " 포함 → 인용)
        val expected = "\"a\\\\\\\"\"" // " a \\ \" " : 백슬래시2 + escape된 따옴표
        assertThat(ProcessBuilderProcessRunner.quoteWindowsArgument(input)).isEqualTo(expected)
    }

    @Test
    fun `trailing 백슬래시 런은 닫는 따옴표 직전에 두배가 된다(단순 replace가 틀리는 엣지케이스)`() {
        // 입력: 'a b\\' (공백 + trailing 백슬래시 2개) → "a b\\\\"
        // 정규 알고리즘: 닫는 따옴표 직전 백슬래시 런(2)을 2배(4)로 만들어 닫는 따옴표가 escape되지 않게 한다.
        val input = "a b\\\\" // a 공백 b 백슬래시 백슬래시
        val expected = "\"a b\\\\\\\\\"" // " a b \\\\ " : 백슬래시 4개 + 닫는 따옴표
        assertThat(ProcessBuilderProcessRunner.quoteWindowsArgument(input)).isEqualTo(expected)
    }

    @Test
    fun `빈 문자열은 빈 큰따옴표 쌍으로 인용된다`() {
        // 빈 인자는 인용하지 않으면 사라지므로 "" 로 보존해야 한다.
        assertThat(ProcessBuilderProcessRunner.quoteWindowsArgument("")).isEqualTo("\"\"")
    }

    // ─────────────────────────────────────────────
    // escapeArgumentsForWindows: 분기 검증
    // ─────────────────────────────────────────────

    @Test
    fun `Windows에서는 실행 파일을 변형하지 않고 인자만 이스케이프한다`() {
        val command = listOf("C:\\npm\\claude.CMD", "-p", "{\"type\":\"object\"}")
        val result = ProcessBuilderProcessRunner.escapeArgumentsForWindows(command, isWindows = true)

        // 인덱스 0(실행 파일)은 그대로, 인덱스 1 이후만 이스케이프.
        assertThat(result[0]).isEqualTo("C:\\npm\\claude.CMD")
        assertThat(result[1]).isEqualTo("-p") // 특수문자 없음 → 무변형
        assertThat(result[2]).isEqualTo("\"{\\\"type\\\":\\\"object\\\"}\"")
    }

    @Test
    fun `non-Windows에서는 인자를 절대 변형하지 않는다`() {
        // Linux/프로덕션 docker: execvp가 verbatim 전달하므로 변형하면 리터럴 따옴표가 깨진다.
        val command = listOf("claude", "-p", "{\"type\":\"object\"}")
        val result = ProcessBuilderProcessRunner.escapeArgumentsForWindows(command, isWindows = false)

        assertThat(result).containsExactlyElementsOf(command)
    }

    @Test
    fun `실행 파일만 있는 명령은 Windows여도 변형되지 않는다`() {
        val command = listOf("claude.CMD")
        val result = ProcessBuilderProcessRunner.escapeArgumentsForWindows(command, isWindows = true)
        assertThat(result).containsExactly("claude.CMD")
    }

    @Test
    fun `빈 명령은 그대로 반환된다`() {
        assertThat(ProcessBuilderProcessRunner.escapeArgumentsForWindows(emptyList(), isWindows = true)).isEmpty()
        assertThat(ProcessBuilderProcessRunner.escapeArgumentsForWindows(emptyList(), isWindows = false)).isEmpty()
    }
}
