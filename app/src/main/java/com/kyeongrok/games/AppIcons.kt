package com.kyeongrok.games

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * material-icons-core에 없는 아이콘이다. 확장 아이콘 묶음은 앱을 크게 키우므로 필요한 것만 그린다.
 * 모두 24×24 격자 기준이다.
 */
object AppIcons {
    /** 3×3 칸. */
    val Sudoku: ImageVector = materialIcon("App.Sudoku") {
        materialPath {
            for (row in 0..2) {
                for (col in 0..2) {
                    moveTo(3f + col * 6.5f, 3f + row * 6.5f)
                    horizontalLineToRelative(5f); verticalLineToRelative(5f); horizontalLineToRelative(-5f); close()
                }
            }
        }
    }

    /** 뒤에 한 장이 비치는 마작 패. 앞 패에는 동그라미 무늬를 뚫는다. */
    val Shanghai: ImageVector = materialIcon("App.Shanghai") {
        materialPath {
            moveTo(8f, 2f); horizontalLineToRelative(12f); verticalLineToRelative(15f); horizontalLineToRelative(-3f)
            verticalLineTo(5f); horizontalLineTo(8f); close()
        }
        materialPath(pathFillType = PathFillType.EvenOdd) {
            moveTo(4f, 7f); horizontalLineToRelative(11f); verticalLineToRelative(15f); horizontalLineTo(4f); close()
            moveTo(9.5f, 11f)
            arcToRelative(3.5f, 3.5f, 0f, true, true, 0f, 7f)
            arcToRelative(3.5f, 3.5f, 0f, true, true, 0f, -7f)
            close()
        }
    }

    /** 세워 놓은 마작 패와 그 아래 리치 막대. */
    val Riichi: ImageVector = materialIcon("App.Riichi") {
        materialPath(pathFillType = PathFillType.EvenOdd) {
            moveTo(6f, 2f); horizontalLineToRelative(12f); verticalLineToRelative(14f); horizontalLineTo(6f); close()
            moveTo(8f, 4f); verticalLineToRelative(10f); horizontalLineToRelative(8f); verticalLineTo(4f); close()
        }
        materialPath {
            moveTo(12f, 6.5f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, 0f, 5f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, 0f, -5f)
            close()
        }
        materialPath(pathFillType = PathFillType.EvenOdd) {
            moveTo(2f, 18.5f); horizontalLineToRelative(20f); verticalLineToRelative(3f); horizontalLineTo(2f); close()
            moveTo(12f, 19.2f)
            arcToRelative(0.8f, 0.8f, 0f, true, true, 0f, 1.6f)
            arcToRelative(0.8f, 0.8f, 0f, true, true, 0f, -1.6f)
            close()
        }
    }

    /** 다이아몬드가 그려진 카드. */
    val FreeCell: ImageVector = materialIcon("App.FreeCell") {
        materialPath(pathFillType = PathFillType.EvenOdd) {
            moveTo(5f, 2f); horizontalLineToRelative(14f); verticalLineToRelative(20f); horizontalLineTo(5f); close()
            moveTo(7f, 4f); verticalLineToRelative(16f); horizontalLineToRelative(10f); verticalLineTo(4f); close()
        }
        materialPath {
            moveTo(12f, 7f); lineToRelative(3.5f, 5f); lineToRelative(-3.5f, 5f); lineToRelative(-3.5f, -5f); close()
        }
    }

    /** 위가 깎인 보석. 윗면과 아랫면 사이를 가는 틈으로 나눈다. */
    val Splendor: ImageVector = materialIcon("App.Splendor") {
        materialPath {
            moveTo(6f, 3f); horizontalLineTo(18f); lineTo(22f, 8.5f); horizontalLineTo(2f); close()
            moveTo(2f, 10f); horizontalLineTo(22f); lineTo(12f, 21.5f); close()
        }
    }
    /** 나란히 놓인 타일 세 장. */
    val Rummikub: ImageVector = materialIcon("App.Rummikub") {
        materialPath {
            for (index in 0..2) {
                moveTo(1.5f + index * 7.5f, 6f)
                horizontalLineToRelative(6f); verticalLineToRelative(12f); horizontalLineToRelative(-6f); close()
            }
        }
    }

    /** 주기율표 한 칸: 테두리 안에 왼쪽 위 번호 자리와 가운데 큰 기호 자리. */
    val ElementMatch: ImageVector = materialIcon("App.ElementMatch") {
        materialPath(pathFillType = PathFillType.EvenOdd) {
            moveTo(3f, 3f); horizontalLineToRelative(18f); verticalLineToRelative(18f); horizontalLineTo(3f); close()
            moveTo(5f, 5f); verticalLineToRelative(14f); horizontalLineToRelative(14f); verticalLineTo(5f); close()
        }
        materialPath {
            moveTo(6.5f, 6.5f); horizontalLineToRelative(3f); verticalLineToRelative(3f); horizontalLineToRelative(-3f); close()
            moveTo(8.5f, 11f); horizontalLineToRelative(7f); verticalLineToRelative(6.5f); horizontalLineToRelative(-7f); close()
        }
    }

    /** 위에 벽돌 두 줄, 아래에 공 하나. */
    val BrickBreaker: ImageVector = materialIcon("App.BrickBreaker") {
        materialPath {
            for (col in 0..2) {
                moveTo(2f + col * 7f, 3f); horizontalLineToRelative(6f); verticalLineToRelative(4f); horizontalLineToRelative(-6f); close()
            }
            for (col in 0..1) {
                moveTo(5.5f + col * 7f, 8.5f); horizontalLineToRelative(6f); verticalLineToRelative(4f); horizontalLineToRelative(-6f); close()
            }
            moveTo(12f, 16f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, 0f, 5f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, 0f, -5f)
            close()
        }
    }

    /** 스택 — 쌓인 상자 셋과 위로 꺼내는 화살표. */
    val NextGreater: ImageVector = materialIcon("App.NextGreater") {
        materialPath {
            for (k in 0..2) {
                moveTo(3f, 19f - k * 5f); horizontalLineToRelative(12f); verticalLineToRelative(4f); horizontalLineToRelative(-12f); close()
            }
            moveTo(19f, 3f); lineTo(23f, 8f); horizontalLineTo(20.25f); verticalLineTo(16f); horizontalLineTo(17.75f)
            verticalLineTo(8f); horizontalLineTo(15f); close()
        }
    }

    /** 다리를 지나는 트럭 — 다리 위 트럭. */
    val Bridge: ImageVector = materialIcon("App.Bridge") {
        materialPath {
            // 짐칸과 운전석
            moveTo(2f, 6f); horizontalLineTo(14f); verticalLineTo(14f); horizontalLineTo(2f); close()
            moveTo(15f, 9f); horizontalLineTo(19f); lineTo(22f, 12f); verticalLineTo(14f); horizontalLineTo(15f); close()
            // 바퀴
            moveTo(6f, 14.5f); arcToRelative(1.75f, 1.75f, 0f, true, true, 0f, 3.5f); arcToRelative(1.75f, 1.75f, 0f, true, true, 0f, -3.5f); close()
            moveTo(18f, 14.5f); arcToRelative(1.75f, 1.75f, 0f, true, true, 0f, 3.5f); arcToRelative(1.75f, 1.75f, 0f, true, true, 0f, -3.5f); close()
            // 다리 바닥과 기둥
            moveTo(1f, 19f); horizontalLineTo(23f); verticalLineTo(20.5f); horizontalLineTo(1f); close()
            moveTo(3f, 20.5f); horizontalLineTo(4.5f); verticalLineTo(23f); horizontalLineTo(3f); close()
            moveTo(19.5f, 20.5f); horizontalLineTo(21f); verticalLineTo(23f); horizontalLineTo(19.5f); close()
        }
    }
}
