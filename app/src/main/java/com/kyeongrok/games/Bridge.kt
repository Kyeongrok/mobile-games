package com.kyeongrok.games

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/**
 * 프로그래머스 「다리를 지나는 트럭」을 1초씩 따라가는 게임.
 *
 * 다리는 길이 [bridgeLength] 칸짜리 큐다(맨 앞 칸 = 곧 나가는 칸, 빈 칸은 0).
 * 1초마다 맨 앞 칸이 빠지고(그 트럭은 다 건넌다), 뒤에 다음 트럭(올리기) 이나 0(기다리기)을 넣는다.
 * 올릴 수 있는 것은 (빠지고 남은 무게 + 다음 트럭) ≤ [limit] 일 때뿐. 트럭 수는 칸 수가 저절로 막는다.
 * 대기 트럭이 다 오르면 다리가 빌 때까지 기다리기만 한다 — 그때의 시간이 답이다.
 * 상태는 Compose 상태라 화면이 바로 읽어 다시 그린다.
 */
class BridgeGame(val bridgeLength: Int, val limit: Int, val trucks: List<Int>) {
    enum class Action { Put, Wait }

    var time by mutableStateOf(0)
        private set
    /** 다리 칸(맨 앞 = 곧 나감). 값은 트럭 번호 + 1, 빈 칸은 0. */
    val bridge = mutableStateListOf<Int>().apply { repeat(bridgeLength) { add(0) } }
    /** 아직 다리에 오르지 않은 첫 트럭의 번호. */
    var next by mutableStateOf(0)
        private set
    /** 다 건넌 트럭 번호(건넌 차례대로). */
    val passed = mutableStateListOf<Int>()
    var mistakes by mutableStateOf(0)
        private set
    var message by mutableStateOf("1초가 지나면 맨 앞 칸은 다리를 떠납니다. 다음 트럭을 올릴까요?")
        private set
    /** 방금 한 일이 코드의 어느 줄인지 — [CODE] 의 번호들. */
    var lastLines by mutableStateOf(setOf(2))
        private set

    val waitingAll: Boolean get() = next < trucks.size
    val finished: Boolean get() = !waitingAll && bridge.all { it == 0 }

    /** 다리 위 무게. */
    fun load(): Int = bridge.sumOf { weightOf(it) }
    /** 맨 앞 칸이 빠지고 남는 무게. */
    fun loadAfterLeave(): Int = load() - weightOf(bridge.first())
    private fun weightOf(cell: Int) = if (cell == 0) 0 else trucks[cell - 1]

    fun expected(): Action? = when {
        finished -> null
        waitingAll && loadAfterLeave() + trucks[next] <= limit -> Action.Put
        else -> Action.Wait
    }

    fun reason(): String {
        val leaving = bridge.first()
        val left = if (leaving != 0) "맨 앞 ${trucks[leaving - 1]} 이 내리고 " else ""
        val rest = loadAfterLeave()
        if (!waitingAll) return "${left}남은 트럭이 건너는 중 — 다리가 빌 때까지 기다립니다."
        val w = trucks[next]
        return if (rest + w <= limit) "${left}다리 무게 $rest + $w = ${rest + w} ≤ $limit → 올립니다."
        else "${left}다리 무게 $rest + $w = ${rest + w} > $limit → 못 올리고 기다립니다."
    }

    /** 고른 일을 한다(1초 흐름). 틀리면 아무것도 바꾸지 않고 false. */
    fun act(action: Action): Boolean {
        val want = expected() ?: return false
        if (action != want) {
            mistakes++
            message = "틀렸어요. ${reason()}"
            return false
        }
        message = reason()
        val draining = !waitingAll
        time++
        val leaving = bridge.removeAt(0)
        if (leaving != 0) passed += leaving - 1
        if (action == Action.Put) {
            bridge += next + 1
            next++
            lastLines = setOf(3, 4, 5, 6, 7)
        } else {
            bridge += 0
            lastLines = if (draining) setOf(12) else setOf(3, 4, 5, 8, 9)
        }
        if (!waitingAll && action == Action.Put) {
            message += " 마지막 트럭이 올랐어요. 코드는 여기서 time($time) + bridge_length($bridgeLength) = ${time + bridgeLength} 를 돌려줍니다."
        }
        if (finished) message = "끝! 모든 트럭이 ${time}초에 다 건넜습니다."
        return true
    }

    /** 코드대로 한 단계. */
    fun step(): Boolean = expected()?.let { act(it) } ?: false

    companion object {
        /** 프로그래머스 예제 1 — bridge_length 2, weight 10, [7, 4, 5, 6] → 8. */
        val EXAMPLE = Triple(2, 10, listOf(7, 4, 5, 6))

        val CODE = listOf(
            "var bridge = new Queue<int>(Enumerable.Repeat(0, bridge_length));",
            "int time = 0, sum = 0, i = 0;",
            "while (i < truck_weights.Length) {",
            "  time++;",
            "  sum -= bridge.Dequeue();",
            "  if (sum + truck_weights[i] <= weight) {",
            "    bridge.Enqueue(truck_weights[i]);",
            "    sum += truck_weights[i++];",
            "  } else {",
            "    bridge.Enqueue(0);",
            "  }",
            "}",
            "return time + bridge_length;",
        )

        const val MIN_COUNT = 2
        const val MAX_COUNT = 9

        fun example() = BridgeGame(EXAMPLE.first, EXAMPLE.second, EXAMPLE.third)

        /** 트럭 count 대, 다리 2 ~ 5 칸, 무게 한도 10 ~ 20, 트럭 무게 1 ~ 한도. */
        fun random(count: Int, random: Random = Random): BridgeGame {
            val limit = random.nextInt(10, 21)
            return BridgeGame(
                random.nextInt(2, 6),
                limit,
                List(count.coerceIn(MIN_COUNT, MAX_COUNT)) { random.nextInt(1, limit + 1) },
            )
        }

        /** 프로그래머스 풀이 그대로 — 검사용. */
        fun solve(bridgeLength: Int, limit: Int, trucks: List<Int>): Int {
            val bridge = ArrayDeque(List(bridgeLength) { 0 })
            var time = 0
            var sum = 0
            var i = 0
            while (i < trucks.size) {
                time++
                sum -= bridge.removeFirst()
                if (sum + trucks[i] <= limit) {
                    bridge.addLast(trucks[i])
                    sum += trucks[i++]
                } else {
                    bridge.addLast(0)
                }
            }
            return time + bridgeLength
        }
    }
}
