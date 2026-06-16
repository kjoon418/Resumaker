package watson.resumaker.generation.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.time.Duration

/**
 * [ProcessBuilderProcessRunner] 단위 테스트(결함3 수정 검증).
 *
 * ## 테스트 전략
 *
 * ### 정상 경로
 * OS별 짧은 실제 명령(`cmd /c echo` / `sh -c printf`)으로 stdout·exitCode 수집을 검증한다.
 *
 * ### 결함3 회귀 테스트 (핵심)
 * `readerJoinTimeoutMillis = 200ms`를 생성자로 주입해 테스트를 빠르게 유지한다.
 *
 * **왜 이 테스트들이 fix를 되돌리면 실패하는가:**
 * - `join(cap)` → `join()`(무제한)으로 되돌리면: sleep 명령 타임아웃 경로의 finally에서 join이 영영
 *   반환하지 않아 `< upperBound` 단언이 실패(또는 JUnit 타임아웃)한다.
 * - `isDaemon = false`로 되돌리면: JVM 종료 차단(별도 통합 문제). 리플렉션 단언이 직접 고정한다.
 *
 * ### StreamReaderThread join cap 단위 검증
 * `StreamReaderThread`는 private이므로 리플렉션으로 인스턴스화해 직접 검증한다.
 * — 영구 블록 InputStream을 주입하고 `join(cap)`이 cap + 마진 안에 반환하는지, isDaemon이 true인지 단언.
 * — fix를 되돌려 join(cap) → join()으로 바꾸면 이 테스트가 JUnit 타임아웃으로 실패한다.
 */
class ProcessBuilderProcessRunnerTest {

    private val isWindows: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    /** 명령 해석을 우회하는 identity seam(실제 PATH/OS 비의존). */
    private val identityResolver = CommandResolver(
        isWindows = false,
        pathDirs = emptyList(),
        pathExts = emptyList(),
        fileExists = { false },
    )

    /**
     * join cap을 200ms로 주입한 runner. 5초 기본값을 기다리지 않고 빠르게 단언할 수 있다.
     * 이 값이 바운드 계산의 기준이 된다.
     */
    private val testJoinCapMillis = 200L
    private val runner = ProcessBuilderProcessRunner(
        commandResolver = identityResolver,
        readerJoinTimeoutMillis = testJoinCapMillis,
    )

    /** OS별로 `text`를 stdout으로 출력하고 즉시 종료하는 짧은 명령. */
    private fun echoCommand(text: String): List<String> =
        if (isWindows) listOf("cmd", "/c", "echo", text)
        else listOf("sh", "-c", "printf '%s\\n' '$text'")

    /** OS별로 오래 잠들어 타임아웃을 유발하는 명령. */
    private fun sleepCommand(seconds: Int): List<String> =
        if (isWindows) listOf("cmd", "/c", "ping", "-n", "${seconds + 1}", "127.0.0.1")
        else listOf("sh", "-c", "sleep $seconds")

    // ─────────────────────────────────────────────
    // 정상 경로
    // ─────────────────────────────────────────────

    @Test
    fun `정상 종료 경로에서 stdout과 종료코드를 온전히 수집한다`() {
        val result = runner.run(echoCommand("hello-resumaker"), stdin = "", timeout = Duration.ofSeconds(10))

        assertThat(result.exitCode).isZero()
        assertThat(result.stdout).contains("hello-resumaker")
    }

    // ─────────────────────────────────────────────
    // 결함3 회귀 테스트
    // ─────────────────────────────────────────────

    /**
     * **결함3 회귀 테스트 (프로세스 경로).**
     *
     * 실 sleep 명령을 짧게 타임아웃시켜 finally 경로로 진입한다. finally의 `join(cap)`이 cap 이내에 반환해
     * 전체 경과가 "프로세스 타임아웃 + stdout join cap + stderr join cap + 여유" 안에 끝나는지 단언한다.
     *
     * **fix 되돌림 시 실패 방식:** `join(cap)` → `join()`으로 바꾸면 sleep 프로세스가 파이프를 닫을 때까지
     * 기다리게 되고(최소 수십 초), `< upperBoundMillis` 단언이 실패한다.
     */
    @Test
    fun `타임아웃 경로에서 ProcessTimeoutException을 던지고 join cap 이내에 반환한다`() {
        val processTimeoutMillis = 300L
        val start = System.nanoTime()

        assertThatThrownBy {
            runner.run(sleepCommand(30), stdin = "", timeout = Duration.ofMillis(processTimeoutMillis))
        }.isInstanceOf(ProcessTimeoutException::class.java)

        val elapsedMillis = (System.nanoTime() - start) / 1_000_000
        // 상한: 프로세스 타임아웃 + stdout join cap + stderr join cap + 여유(500ms).
        // join()이 무제한이면 sleep 30초를 기다리므로 이 단언이 실패한다.
        val upperBoundMillis = processTimeoutMillis + testJoinCapMillis * 2 + 500
        assertThat(elapsedMillis).isLessThan(upperBoundMillis)
    }

    /**
     * **결함3 핵심 단위 테스트: StreamReaderThread join cap + isDaemon.**
     *
     * `StreamReaderThread`(private)를 리플렉션으로 인스턴스화해 직접 검증한다.
     * 영구 블록 InputStream을 주입하고:
     * - `isDaemon == true` — orphan이 파이프를 잡아도 JVM 종료를 막지 않음.
     * - `join(cap)`이 cap + 마진 안에 반환 — EOF 없이도 요청 스레드가 진행함.
     *
     * **fix 되돌림 시 실패 방식:**
     * - `join(cap)` → `join()`으로 바꾸면: join이 영영 반환하지 않아 JUnit 타임아웃(기본 300s)으로 실패.
     * - `isDaemon = false`로 바꾸면: `isDaemon` 단언이 직접 실패.
     */
    @Test
    fun `StreamReaderThread는 데몬이고 영구 블록 InputStream에서도 join cap 이내에 반환한다`() {
        // 인터럽트될 때까지 read()가 영구 블록하는 InputStream stub.
        // 실제 orphan(손자 프로세스)이 stdout 파이프를 잡고 있어 EOF가 오지 않는 상황을 재현한다.
        val blockingStream = object : InputStream() {
            override fun read(): Int {
                try {
                    Thread.sleep(Long.MAX_VALUE)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                return -1
            }
        }

        // StreamReaderThread는 private static nested class(Kotlin private class)이므로
        // outer instance 파라미터 없이 InputStream만으로 생성한다.
        val outerClass = ProcessBuilderProcessRunner::class.java
        val innerClass = outerClass.declaredClasses
            .first { it.simpleName == "StreamReaderThread" }
        val ctor = innerClass.getDeclaredConstructor(InputStream::class.java)
        ctor.isAccessible = true
        val thread = ctor.newInstance(blockingStream) as Thread

        // isDaemon = true 단언 (시작 전에 확인 — 시작 후에는 변경 불가).
        assertThat(thread.isDaemon)
            .withFailMessage("StreamReaderThread는 isDaemon=true여야 합니다(결함3: orphan이 JVM 종료를 막지 않게).")
            .isTrue()

        thread.start()

        val start = System.nanoTime()
        thread.join(testJoinCapMillis) // fix: 타임아웃 있는 join
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000

        // join(cap)이 cap + 150ms 안에 반환해야 한다 (영구 블록이면 타임아웃 걸려 실패).
        assertThat(elapsedMillis)
            .withFailMessage("join(${testJoinCapMillis}ms)이 ${elapsedMillis}ms 걸렸습니다. 영구 블록이면 fix가 되돌아간 것입니다.")
            .isLessThan(testJoinCapMillis + 150)
        // 스레드는 아직 살아 있어야 정상 (EOF를 못 받아 블록 중).
        assertThat(thread.isAlive).isTrue()

        // 정리: 인터럽트로 블록 해제 후 종료 대기.
        thread.interrupt()
        thread.join(500)
    }
}
