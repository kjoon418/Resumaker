package watson.resumaker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 디자인 시스템 §6은 경험유형 아이콘을 Material Icons(extended)로 대체 매핑하도록 명시한다.
 * 그러나 Compose Multiplatform 1.11.x에는 `material-icons-extended` 아티팩트가 제공되지 않으므로,
 * 필요한 아이콘만 24dp viewport 기준 vector path로 직접 정의해 외부 의존 없이 동일 형태를 제공한다.
 * path 데이터는 Material Symbols(Outlined) 원본 형상을 24x24 기준으로 차용했다.
 *
 * 단일 색 채움만 쓰며, 실제 틴트는 사용처(`ExperienceIconChip`)에서 `tint`로 덮어쓴다.
 */
object RmIcons {

    private fun icon(name: String, pathData: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(pathData).build()

    private fun ImageVector.Builder.fill(block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) {
        path(fill = SolidColor(Color.Black), pathBuilder = block)
    }

    /** 뒤로(chevron-left). */
    val ChevronLeft: ImageVector = icon("RmChevronLeft") {
        fill {
            moveTo(15.4f, 7.4f)
            lineTo(14f, 6f)
            lineTo(8f, 12f)
            lineTo(14f, 18f)
            lineTo(15.4f, 16.6f)
            lineTo(10.8f, 12f)
            close()
        }
    }

    /** 더보기/이동(chevron-right). */
    val ChevronRight: ImageVector = icon("RmChevronRight") {
        fill {
            moveTo(8.6f, 7.4f)
            lineTo(10f, 6f)
            lineTo(16f, 12f)
            lineTo(10f, 18f)
            lineTo(8.6f, 16.6f)
            lineTo(13.2f, 12f)
            close()
        }
    }

    /** 홈 탭 — 집. */
    val Home: ImageVector = icon("RmHome") {
        fill {
            moveTo(12f, 3f)
            lineTo(2f, 12f)
            horizontalLineTo(5f)
            verticalLineTo(20f)
            horizontalLineTo(10f)
            verticalLineTo(14f)
            horizontalLineTo(14f)
            verticalLineTo(20f)
            horizontalLineTo(19f)
            verticalLineTo(12f)
            horizontalLineTo(22f)
            close()
        }
    }

    /** 경험 탭 — 문서편집(노트). */
    val Note: ImageVector = icon("RmNote") {
        fill {
            moveTo(14f, 2f)
            horizontalLineTo(6f)
            curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
            verticalLineTo(20f)
            curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
            horizontalLineTo(18f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            verticalLineTo(8f)
            close()
            moveTo(16f, 18f)
            horizontalLineTo(8f)
            verticalLineTo(16f)
            horizontalLineTo(16f)
            close()
            moveTo(16f, 14f)
            horizontalLineTo(8f)
            verticalLineTo(12f)
            horizontalLineTo(16f)
            close()
            moveTo(13f, 9f)
            verticalLineTo(3.5f)
            lineTo(18.5f, 9f)
            close()
        }
    }

    /** 목표 탭 — 과녁. */
    val Target: ImageVector = icon("RmTarget") {
        fill {
            moveTo(12f, 2f)
            curveTo(6.5f, 2f, 2f, 6.5f, 2f, 12f)
            curveTo(2f, 17.5f, 6.5f, 22f, 12f, 22f)
            curveTo(17.5f, 22f, 22f, 17.5f, 22f, 12f)
            curveTo(22f, 6.5f, 17.5f, 2f, 12f, 2f)
            close()
            moveTo(12f, 20f)
            curveTo(7.6f, 20f, 4f, 16.4f, 4f, 12f)
            curveTo(4f, 7.6f, 7.6f, 4f, 12f, 4f)
            curveTo(16.4f, 4f, 20f, 7.6f, 20f, 12f)
            curveTo(20f, 16.4f, 16.4f, 20f, 12f, 20f)
            close()
            moveTo(12f, 7f)
            curveTo(9.2f, 7f, 7f, 9.2f, 7f, 12f)
            curveTo(7f, 14.8f, 9.2f, 17f, 12f, 17f)
            curveTo(14.8f, 17f, 17f, 14.8f, 17f, 12f)
            curveTo(17f, 9.2f, 14.8f, 7f, 12f, 7f)
            close()
            moveTo(12f, 14f)
            curveTo(10.9f, 14f, 10f, 13.1f, 10f, 12f)
            curveTo(10f, 10.9f, 10.9f, 10f, 12f, 10f)
            curveTo(13.1f, 10f, 14f, 10.9f, 14f, 12f)
            curveTo(14f, 13.1f, 13.1f, 14f, 12f, 14f)
            close()
        }
    }

    /** 마이 탭 — 사람. */
    val Person: ImageVector = icon("RmPerson") {
        fill {
            moveTo(12f, 12f)
            curveTo(14.2f, 12f, 16f, 10.2f, 16f, 8f)
            curveTo(16f, 5.8f, 14.2f, 4f, 12f, 4f)
            curveTo(9.8f, 4f, 8f, 5.8f, 8f, 8f)
            curveTo(8f, 10.2f, 9.8f, 12f, 12f, 12f)
            close()
            moveTo(12f, 14f)
            curveTo(9.3f, 14f, 4f, 15.3f, 4f, 18f)
            verticalLineTo(20f)
            horizontalLineTo(20f)
            verticalLineTo(18f)
            curveTo(20f, 15.3f, 14.7f, 14f, 12f, 14f)
            close()
        }
    }

    /** 추가(+). */
    val Add: ImageVector = icon("RmAdd") {
        fill {
            moveTo(19f, 13f)
            horizontalLineTo(13f)
            verticalLineTo(19f)
            horizontalLineTo(11f)
            verticalLineTo(13f)
            horizontalLineTo(5f)
            verticalLineTo(11f)
            horizontalLineTo(11f)
            verticalLineTo(5f)
            horizontalLineTo(13f)
            verticalLineTo(11f)
            horizontalLineTo(19f)
            close()
        }
    }

    /** 정보 안내(info). */
    val Info: ImageVector = icon("RmInfo") {
        fill {
            moveTo(12f, 2f)
            curveTo(6.5f, 2f, 2f, 6.5f, 2f, 12f)
            curveTo(2f, 17.5f, 6.5f, 22f, 12f, 22f)
            curveTo(17.5f, 22f, 22f, 17.5f, 22f, 12f)
            curveTo(22f, 6.5f, 17.5f, 2f, 12f, 2f)
            close()
            moveTo(13f, 17f)
            horizontalLineTo(11f)
            verticalLineTo(11f)
            horizontalLineTo(13f)
            close()
            moveTo(13f, 9f)
            horizontalLineTo(11f)
            verticalLineTo(7f)
            horizontalLineTo(13f)
            close()
        }
    }

    /** 팁/회상 보조(lightbulb). */
    val Lightbulb: ImageVector = icon("RmLightbulb") {
        fill {
            moveTo(12f, 2f)
            curveTo(8.1f, 2f, 5f, 5.1f, 5f, 9f)
            curveTo(5f, 11.4f, 6.2f, 13.5f, 8f, 14.7f)
            verticalLineTo(17f)
            horizontalLineTo(16f)
            verticalLineTo(14.7f)
            curveTo(17.8f, 13.5f, 19f, 11.4f, 19f, 9f)
            curveTo(19f, 5.1f, 15.9f, 2f, 12f, 2f)
            close()
            moveTo(9f, 20f)
            horizontalLineTo(15f)
            verticalLineTo(21f)
            curveTo(15f, 21.6f, 14.6f, 22f, 14f, 22f)
            horizontalLineTo(10f)
            curveTo(9.4f, 22f, 9f, 21.6f, 9f, 21f)
            close()
        }
    }

    /** 산출물 준비중(sparkles). */
    val Sparkles: ImageVector = icon("RmSparkles") {
        fill {
            moveTo(12f, 2f)
            lineTo(14f, 8f)
            lineTo(20f, 10f)
            lineTo(14f, 12f)
            lineTo(12f, 18f)
            lineTo(10f, 12f)
            lineTo(4f, 10f)
            lineTo(10f, 8f)
            close()
            moveTo(18f, 14f)
            lineTo(19f, 17f)
            lineTo(22f, 18f)
            lineTo(19f, 19f)
            lineTo(18f, 22f)
            lineTo(17f, 19f)
            lineTo(14f, 18f)
            lineTo(17f, 17f)
            close()
        }
    }

    /** 복사(userId 복사). */
    val Copy: ImageVector = icon("RmCopy") {
        fill {
            moveTo(16f, 1f)
            horizontalLineTo(4f)
            curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f)
            verticalLineTo(17f)
            horizontalLineTo(4f)
            verticalLineTo(3f)
            horizontalLineTo(16f)
            close()
            moveTo(19f, 5f)
            horizontalLineTo(8f)
            curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f)
            verticalLineTo(21f)
            curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f)
            horizontalLineTo(19f)
            curveTo(20.1f, 23f, 21f, 22.1f, 21f, 21f)
            verticalLineTo(7f)
            curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
            close()
            moveTo(19f, 21f)
            horizontalLineTo(8f)
            verticalLineTo(7f)
            horizontalLineTo(19f)
            close()
        }
    }

    /** 빈 상태 — 빈 문서/인박스. */
    val Inbox: ImageVector = icon("RmInbox") {
        fill {
            moveTo(19f, 3f)
            horizontalLineTo(5f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            verticalLineTo(19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            horizontalLineTo(19f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            verticalLineTo(5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            close()
            moveTo(19f, 15f)
            horizontalLineTo(15f)
            curveTo(15f, 16.7f, 13.7f, 18f, 12f, 18f)
            curveTo(10.3f, 18f, 9f, 16.7f, 9f, 15f)
            horizontalLineTo(5f)
            verticalLineTo(5f)
            horizontalLineTo(19f)
            close()
        }
    }

    /** 닫기/삭제(x). */
    val Close: ImageVector = icon("RmClose") {
        fill {
            moveTo(18.3f, 5.7f)
            lineTo(16.9f, 4.3f)
            lineTo(12f, 9.2f)
            lineTo(7.1f, 4.3f)
            lineTo(5.7f, 5.7f)
            lineTo(10.6f, 10.6f)
            lineTo(5.7f, 15.5f)
            lineTo(7.1f, 16.9f)
            lineTo(12f, 12f)
            lineTo(16.9f, 16.9f)
            lineTo(18.3f, 15.5f)
            lineTo(13.4f, 10.6f)
            close()
        }
    }

    /** PROJECT — 코드 꺾쇠 `< >` (fa-code 대체). */
    val Code: ImageVector = icon("RmCode") {
        fill {
            // 왼쪽 꺾쇠 <
            moveTo(9.4f, 16.6f)
            lineTo(4.8f, 12f)
            lineTo(9.4f, 7.4f)
            lineTo(8f, 6f)
            lineTo(2f, 12f)
            lineTo(8f, 18f)
            close()
            // 오른쪽 꺾쇠 >
            moveTo(14.6f, 16.6f)
            lineTo(19.2f, 12f)
            lineTo(14.6f, 7.4f)
            lineTo(16f, 6f)
            lineTo(22f, 12f)
            lineTo(16f, 18f)
            close()
        }
    }

    /** JOB — 서류가방 (fa-briefcase 대체). */
    val Work: ImageVector = icon("RmWork") {
        fill {
            moveTo(20f, 6f)
            horizontalLineTo(16f)
            verticalLineTo(4f)
            curveTo(16f, 2.9f, 15.1f, 2f, 14f, 2f)
            horizontalLineTo(10f)
            curveTo(8.9f, 2f, 8f, 2.9f, 8f, 4f)
            verticalLineTo(6f)
            horizontalLineTo(4f)
            curveTo(2.9f, 6f, 2f, 6.9f, 2f, 8f)
            verticalLineTo(19f)
            curveTo(2f, 20.1f, 2.9f, 21f, 4f, 21f)
            horizontalLineTo(20f)
            curveTo(21.1f, 21f, 22f, 20.1f, 22f, 19f)
            verticalLineTo(8f)
            curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
            close()
            moveTo(14f, 6f)
            horizontalLineTo(10f)
            verticalLineTo(4f)
            horizontalLineTo(14f)
            close()
        }
    }

    /** EXTRACURRICULAR — 사람 그룹 (fa-users 대체). */
    val Groups: ImageVector = icon("RmGroups") {
        fill {
            // 가운데 큰 사람
            moveTo(12f, 12.5f)
            curveTo(13.66f, 12.5f, 15f, 11.16f, 15f, 9.5f)
            curveTo(15f, 7.84f, 13.66f, 6.5f, 12f, 6.5f)
            curveTo(10.34f, 6.5f, 9f, 7.84f, 9f, 9.5f)
            curveTo(9f, 11.16f, 10.34f, 12.5f, 12f, 12.5f)
            close()
            moveTo(7f, 18.5f)
            curveTo(7f, 16.5f, 10f, 14.5f, 12f, 14.5f)
            curveTo(14f, 14.5f, 17f, 16.5f, 17f, 18.5f)
            verticalLineTo(19f)
            horizontalLineTo(7f)
            close()
            // 좌측 작은 사람
            moveTo(5.5f, 11.5f)
            curveTo(6.6f, 11.5f, 7.5f, 10.6f, 7.5f, 9.5f)
            curveTo(7.5f, 8.4f, 6.6f, 7.5f, 5.5f, 7.5f)
            curveTo(4.4f, 7.5f, 3.5f, 8.4f, 3.5f, 9.5f)
            curveTo(3.5f, 10.6f, 4.4f, 11.5f, 5.5f, 11.5f)
            close()
            // 우측 작은 사람
            moveTo(18.5f, 11.5f)
            curveTo(19.6f, 11.5f, 20.5f, 10.6f, 20.5f, 9.5f)
            curveTo(20.5f, 8.4f, 19.6f, 7.5f, 18.5f, 7.5f)
            curveTo(17.4f, 7.5f, 16.5f, 8.4f, 16.5f, 9.5f)
            curveTo(16.5f, 10.6f, 17.4f, 11.5f, 18.5f, 11.5f)
            close()
        }
    }

    /** AWARD — 트로피 (fa-trophy 대체). */
    val Trophy: ImageVector = icon("RmTrophy") {
        fill {
            moveTo(18f, 4f)
            verticalLineTo(3f)
            horizontalLineTo(6f)
            verticalLineTo(4f)
            horizontalLineTo(2f)
            verticalLineTo(7f)
            curveTo(2f, 9.2f, 3.8f, 11f, 6f, 11f)
            curveTo(6.4f, 12.8f, 8f, 14.2f, 10f, 14.7f)
            verticalLineTo(18f)
            horizontalLineTo(7f)
            verticalLineTo(21f)
            horizontalLineTo(17f)
            verticalLineTo(18f)
            horizontalLineTo(14f)
            verticalLineTo(14.7f)
            curveTo(16f, 14.2f, 17.6f, 12.8f, 18f, 11f)
            curveTo(20.2f, 11f, 22f, 9.2f, 22f, 7f)
            verticalLineTo(4f)
            close()
            moveTo(6f, 9f)
            curveTo(4.9f, 9f, 4f, 8.1f, 4f, 7f)
            verticalLineTo(6f)
            horizontalLineTo(6f)
            close()
            moveTo(20f, 7f)
            curveTo(20f, 8.1f, 19.1f, 9f, 18f, 9f)
            verticalLineTo(6f)
            horizontalLineTo(20f)
            close()
        }
    }

    /** 비밀번호 표시 토글 — 눈 (보임). */
    val Eye: ImageVector = icon("RmEye") {
        fill {
            moveTo(12f, 5f)
            curveTo(7f, 5f, 2.7f, 8.1f, 1f, 12f)
            curveTo(2.7f, 15.9f, 7f, 19f, 12f, 19f)
            curveTo(17f, 19f, 21.3f, 15.9f, 23f, 12f)
            curveTo(21.3f, 8.1f, 17f, 5f, 12f, 5f)
            close()
            moveTo(12f, 16.5f)
            curveTo(9.5f, 16.5f, 7.5f, 14.5f, 7.5f, 12f)
            curveTo(7.5f, 9.5f, 9.5f, 7.5f, 12f, 7.5f)
            curveTo(14.5f, 7.5f, 16.5f, 9.5f, 16.5f, 12f)
            curveTo(16.5f, 14.5f, 14.5f, 16.5f, 12f, 16.5f)
            close()
            moveTo(12f, 9.5f)
            curveTo(10.6f, 9.5f, 9.5f, 10.6f, 9.5f, 12f)
            curveTo(9.5f, 13.4f, 10.6f, 14.5f, 12f, 14.5f)
            curveTo(13.4f, 14.5f, 14.5f, 13.4f, 14.5f, 12f)
            curveTo(14.5f, 10.6f, 13.4f, 9.5f, 12f, 9.5f)
            close()
        }
    }

    /** 비밀번호 표시 토글 — 눈 가림 (숨김). */
    val EyeOff: ImageVector = icon("RmEyeOff") {
        fill {
            moveTo(12f, 7f)
            curveTo(14.8f, 7f, 17f, 9.2f, 17f, 12f)
            curveTo(17f, 12.6f, 16.9f, 13.3f, 16.6f, 13.9f)
            lineTo(19.5f, 16.8f)
            curveTo(21f, 15.5f, 22.3f, 13.9f, 23f, 12f)
            curveTo(21.3f, 8.1f, 17f, 5f, 12f, 5f)
            curveTo(10.6f, 5f, 9.3f, 5.3f, 8f, 5.7f)
            lineTo(10.2f, 7.9f)
            curveTo(10.8f, 7.3f, 11.4f, 7f, 12f, 7f)
            close()
            moveTo(2.8f, 4.3f)
            lineTo(1.4f, 5.7f)
            lineTo(4f, 8.3f)
            curveTo(2.7f, 9.3f, 1.7f, 10.5f, 1f, 12f)
            curveTo(2.7f, 15.9f, 7f, 19f, 12f, 19f)
            curveTo(13.6f, 19f, 15.1f, 18.7f, 16.4f, 18.1f)
            lineTo(20.3f, 22f)
            lineTo(21.7f, 20.6f)
            close()
            moveTo(9.5f, 11f)
            lineTo(12.9f, 14.4f)
            curveTo(12.6f, 14.5f, 12.3f, 14.5f, 12f, 14.5f)
            curveTo(10.6f, 14.5f, 9.5f, 13.4f, 9.5f, 12f)
            curveTo(9.5f, 11.7f, 9.5f, 11.3f, 9.5f, 11f)
            close()
        }
    }

    /** LEARNING — 학사모 (fa-graduation-cap 대체). */
    val School: ImageVector = icon("RmSchool") {
        fill {
            moveTo(12f, 3f)
            lineTo(1f, 9f)
            lineTo(12f, 15f)
            lineTo(21f, 10.09f)
            verticalLineTo(17f)
            horizontalLineTo(23f)
            verticalLineTo(9f)
            close()
            moveTo(5f, 13.18f)
            verticalLineTo(16.18f)
            lineTo(12f, 20f)
            lineTo(19f, 16.18f)
            verticalLineTo(13.18f)
            lineTo(12f, 17f)
            close()
        }
    }
}
