package com.kyeongrok.games

import kotlin.math.abs
import kotlin.random.Random

/** 보석 하나. id 는 판에서 움직여도 그대로라 화면이 이것으로 미끄러지는 모습을 그린다. */
class Gem(val id: Int, val kind: Int)

/** 판의 한 칸(줄 · 열). */
data class Cell(val row: Int, val col: Int) {
    fun isNextTo(other: Cell) = abs(row - other.row) + abs(col - other.col) == 1
}

/**
 * 보석 맞추기(3개 맞추기). [SIZE]×[SIZE] 판에서 이웃한 두 보석을 바꿔 같은 종류가 가로나 세로로 3개 이상 늘어서게 하면 사라진다.
 * 빈 칸은 위 보석이 떨어져 메우고, 맨 위에는 새 보석이 생긴다. 새로 늘어선 줄이 또 생기면 연쇄로 이어지고 연쇄마다 점수 배수가 오른다.
 * 이동은 [MOVES]번. 둘 수 있는 수가 하나도 없으면 판을 섞는다.
 *
 * 한 번 두기: [swap] → (반복) [matches] 가 비지 않으면 [clear] 후 [collapse] → [endMove].
 * 화면이 단계마다 그림을 보여 줄 수 있게 나누어 두었다. [play] 는 이 단계를 한 번에 한다.
 */
class JewelGame(private val random: Random = Random) {
    val board: Array<Array<Gem?>> = Array(SIZE) { arrayOfNulls(SIZE) }
    var score = 0
        private set
    var movesLeft = MOVES
        private set
    /** 이번 수의 연쇄 횟수(1부터). 점수 배수. */
    var chain = 0
        private set
    /** 이번 수에서 얻은 점수. */
    var moveScore = 0
        private set
    /** 한 수에서 가장 길게 이어진 연쇄. */
    var bestChain = 0
        private set
    private var nextId = 0

    val over: Boolean get() = movesLeft <= 0

    init {
        fill()
    }

    operator fun get(cell: Cell): Gem? = board[cell.row][cell.col]

    private fun newGem(kind: Int = random.nextInt(KINDS)) = Gem(nextId++, kind)

    /** 늘어선 줄도 없고 둘 수 있는 수는 있는 판으로 새로 채운다. */
    private fun fill() {
        do {
            for (row in 0 until SIZE) for (col in 0 until SIZE) {
                var kind: Int
                do {
                    kind = random.nextInt(KINDS)
                } while (
                    (col >= 2 && board[row][col - 1]?.kind == kind && board[row][col - 2]?.kind == kind) ||
                    (row >= 2 && board[row - 1][col]?.kind == kind && board[row - 2][col]?.kind == kind)
                )
                board[row][col] = newGem(kind)
            }
        } while (hint() == null)
    }

    /** 이웃한 두 칸을 바꾼다. 늘어서는 줄이 없으면 되돌리고 false. 이동이 남지 않았거나 이웃이 아니어도 false. */
    fun swap(a: Cell, b: Cell): Boolean {
        if (over || !a.isNextTo(b)) return false
        exchange(a, b)
        if (matches().isEmpty()) {
            exchange(a, b)
            return false
        }
        movesLeft--
        chain = 0
        moveScore = 0
        return true
    }

    private fun exchange(a: Cell, b: Cell) {
        val t = board[a.row][a.col]
        board[a.row][a.col] = board[b.row][b.col]
        board[b.row][b.col] = t
    }

    /** 지금 가로나 세로로 3개 이상 늘어선 칸 모두. */
    fun matches(): Set<Cell> {
        val out = HashSet<Cell>()
        for (row in 0 until SIZE) {
            var start = 0
            for (col in 1..SIZE) {
                if (col == SIZE || board[row][col]?.kind != board[row][start]?.kind || board[row][col] == null) {
                    if (col - start >= 3 && board[row][start] != null) for (c in start until col) out += Cell(row, c)
                    start = col
                }
            }
        }
        for (col in 0 until SIZE) {
            var start = 0
            for (row in 1..SIZE) {
                if (row == SIZE || board[row][col]?.kind != board[start][col]?.kind || board[row][col] == null) {
                    if (row - start >= 3 && board[start][col] != null) for (r in start until row) out += Cell(r, col)
                    start = row
                }
            }
        }
        return out
    }

    /** 늘어선 칸을 지우고 점수를 더한다 — 보석 하나에 10점 × 연쇄 횟수. 얻은 점수를 돌려준다. */
    fun clear(cells: Set<Cell>): Int {
        if (cells.isEmpty()) return 0
        chain++
        bestChain = maxOf(bestChain, chain)
        cells.forEach { board[it.row][it.col] = null }
        val gained = cells.size * 10 * chain
        score += gained
        moveScore += gained
        return gained
    }

    /** 위 보석을 떨어뜨려 빈 칸을 메우고 맨 위를 새 보석으로 채운다. */
    fun collapse() {
        for (col in 0 until SIZE) {
            var write = SIZE - 1
            for (row in SIZE - 1 downTo 0) {
                val gem = board[row][col] ?: continue
                board[row][col] = null
                board[write--][col] = gem
            }
            while (write >= 0) board[write--][col] = newGem()
        }
    }

    /** 수가 끝났을 때 — 둘 수 있는 수가 없으면 섞고 true. */
    fun endMove(): Boolean {
        if (hint() != null) return false
        shuffle()
        return true
    }

    /** 같은 보석들을 다시 늘어놓는다. 늘어선 줄이 없고 둘 수 있는 수가 있을 때까지. 섞어서 안 되면 새로 채운다. */
    fun shuffle() {
        val gems = board.flatMap { it.toList() }.filterNotNull().toMutableList()
        repeat(100) {
            gems.shuffle(random)
            for (i in gems.indices) board[i / SIZE][i % SIZE] = gems[i]
            if (matches().isEmpty() && hint() != null) return
        }
        fill()
    }

    /** 늘어서는 줄이 생기는 바꾸기 하나. 없으면 null. */
    fun hint(): Pair<Cell, Cell>? {
        for (row in 0 until SIZE) for (col in 0 until SIZE) {
            val a = Cell(row, col)
            for (b in listOf(Cell(row, col + 1), Cell(row + 1, col))) {
                if (b.row >= SIZE || b.col >= SIZE) continue
                exchange(a, b)
                val ok = matches().isNotEmpty()
                exchange(a, b)
                if (ok) return a to b
            }
        }
        return null
    }

    /** [swap] 부터 [endMove] 까지 한 번에. 바꿀 수 없으면 false. */
    fun play(a: Cell, b: Cell): Boolean {
        if (!swap(a, b)) return false
        while (true) {
            val cells = matches()
            if (cells.isEmpty()) break
            clear(cells)
            collapse()
        }
        endMove()
        return true
    }

    companion object {
        const val SIZE = 8
        const val KINDS = 6
        const val MOVES = 30
    }
}
