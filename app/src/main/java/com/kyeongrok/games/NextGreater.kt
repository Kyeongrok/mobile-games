package com.kyeongrok.games

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/**
 * 스택으로 「뒤에 있는 큰 수」 찾기를 한 단계씩 따라가는 게임.
 *
 * ```
 * for (i in numbers.indices) {
 *     while (stack.isNotEmpty() && numbers[stack.peek()] < numbers[i]) answer[stack.pop()] = numbers[i]
 *     stack.push(i)
 * }
 * ```
 * 매 단계 할 일은 둘 중 하나다 — 맨 위 값이 지금 값보다 작으면 꺼내고(Pop, 그 칸의 답은 지금 값), 아니면 지금 칸을 넣는다(Push).
 * 플레이어가 고른 것이 위 코드와 다르면 틀린 것으로 센다.
 * 상태는 Compose 상태(mutableStateOf · mutableStateListOf)라 화면이 바로 읽어 다시 그린다.
 */
class NextGreaterGame(val numbers: List<Int>) {
    enum class Action { Pop, Push }

    /** 지금 보고 있는 칸. numbers.size 이면 끝. */
    var index by mutableStateOf(0)
        private set
    /** 스택(아래 → 위)에 든 칸 번호. */
    val stack = mutableStateListOf<Int>()
    /** 답 — 아직 모르면 null, 끝나면 남은 칸은 -1. */
    val answer = mutableStateListOf<Int?>().apply { repeat(numbers.size) { add(null) } }
    var mistakes by mutableStateOf(0)
        private set
    /** 마지막으로 한 일의 설명. */
    var message by mutableStateOf("맨 위 값과 지금 값을 견줘 Pop 이나 Push 를 고르세요.")
        private set
    /** 마지막으로 꺼낸 칸 — 화면에서 강조한다. */
    var lastPopped by mutableStateOf<Int?>(null)
        private set

    val finished: Boolean get() = index >= numbers.size
    val top: Int? get() = stack.lastOrNull()

    /** 코드대로라면 지금 할 일. 끝났으면 null. */
    fun expected(): Action? = when {
        finished -> null
        top != null && numbers[top!!] < numbers[index] -> Action.Pop
        else -> Action.Push
    }

    /** 왜 그 일을 해야 하는지. */
    fun reason(): String {
        val t = top
        val current = numbers[index]
        return when {
            t == null -> "스택이 비었으니 [$index] 를 넣습니다."
            numbers[t] < current -> "맨 위 ${numbers[t]} < 지금 $current → [$t] 의 답은 $current, 꺼냅니다."
            else -> "맨 위 ${numbers[t]} ≥ 지금 $current → 더 꺼낼 것이 없으니 [$index] 를 넣습니다."
        }
    }

    /** 고른 일을 한다. 맞으면 하고 true, 틀리면 아무것도 바꾸지 않고 false. */
    fun act(action: Action): Boolean {
        val want = expected() ?: return false
        if (action != want) {
            mistakes++
            message = "틀렸어요. ${reason()}"
            return false
        }
        message = reason()
        if (action == Action.Pop) {
            val popped = stack.removeAt(stack.lastIndex)
            answer[popped] = numbers[index]
            lastPopped = popped
            lastLines = setOf(4, 5, 6)
        } else {
            stack += index
            lastPopped = null
            lastLines = setOf(8)
            index++
            if (finished) {
                lastLines = setOf(10)
                stack.forEach { answer[it] = -1 }
                message = "끝! 스택에 남은 ${stack.size}칸은 뒤에 더 큰 수가 없어서 -1 입니다."
            }
        }
        return true
    }

    /** 코드대로 한 단계(자동 보기 · 힌트). */
    fun step(): Boolean = expected()?.let { act(it) } ?: false

    /** 방금 한 일이 코드의 어느 줄인지 — [CODE] 의 번호들. */
    var lastLines by mutableStateOf(setOf(3))
        private set

    companion object {
        val EXAMPLE = listOf(2, 3, 3, 5)

        /** 화면에 보여 줄 C# 코드. */
        val CODE = listOf(
            "int[] answer = new int[numbers.Length];",
            "Array.Fill(answer, -1);",
            "var stack = new Stack<int>();",
            "for (int i = 0; i < numbers.Length; i++) {",
            "  while (stack.Count > 0 &&",
            "         numbers[stack.Peek()] < numbers[i])",
            "    answer[stack.Pop()] = numbers[i];",
            "",
            "  stack.Push(i);",
            "}",
            "return answer;",
        )

        const val MIN_COUNT = 2
        const val MAX_COUNT = 9

        /** count 칸([MIN_COUNT] ~ [MAX_COUNT]), 값은 1 ~ 9 (겹치는 수도 나온다). */
        fun random(count: Int, random: Random = Random): NextGreaterGame =
            NextGreaterGame(List(count.coerceIn(MIN_COUNT, MAX_COUNT)) { random.nextInt(1, 10) })

        /** 정답(보통 풀이) — 검사용. */
        fun solve(numbers: List<Int>): List<Int> {
            val answer = MutableList(numbers.size) { -1 }
            val stack = ArrayDeque<Int>()
            for (i in numbers.indices) {
                while (stack.isNotEmpty() && numbers[stack.last()] < numbers[i]) answer[stack.removeLast()] = numbers[i]
                stack.addLast(i)
            }
            return answer
        }
    }
}
