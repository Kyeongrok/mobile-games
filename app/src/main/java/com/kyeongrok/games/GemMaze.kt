package com.kyeongrok.games

import kotlin.math.hypot
import kotlin.math.pow
import kotlin.random.Random

/** 보석 미로의 보석 종류. 종류마다 공격 방식이 다르다. */
enum class MazeGemKind(val label: String, val effect: String) {
    Fire("불꽃", "맞은 적 주변에도 절반 피해"),
    Ice("얼음", "맞은 적을 잠깐 느리게"),
    Bolt("번개", "약하지만 아주 빠르게 쏨"),
    Venom("독", "맞은 적이 3초 동안 계속 피해"),
}

/** 탑(남긴 보석) 또는 이번 라운드에 놓은 보석. level 은 1~[GemMazeGame.MAX_LEVEL]. */
class MazeGem(val kind: MazeGemKind, val level: Int, val row: Int, val col: Int) {
    var cooldown = 0f

    val damage: Float get() = BASE_DAMAGE.getValue(kind) * GROWTH.pow(level - 1)
    val interval: Float get() = INTERVAL.getValue(kind)
    val range: Float get() = RANGE.getValue(kind) + 0.15f * (level - 1)

    companion object {
        const val GROWTH = 2.4f
        private val BASE_DAMAGE = mapOf(MazeGemKind.Fire to 9f, MazeGemKind.Ice to 5f, MazeGemKind.Bolt to 3f, MazeGemKind.Venom to 3f)
        private val INTERVAL = mapOf(MazeGemKind.Fire to 1.1f, MazeGemKind.Ice to 0.9f, MazeGemKind.Bolt to 0.3f, MazeGemKind.Venom to 0.9f)
        private val RANGE = mapOf(MazeGemKind.Fire to 2.2f, MazeGemKind.Ice to 2.5f, MazeGemKind.Bolt to 2.0f, MazeGemKind.Venom to 2.4f)
    }
}

class MazeEnemy(val id: Int, val maxHp: Float, val speed: Float, val boss: Boolean, val fast: Boolean) {
    var hp = maxHp
    /** 길을 따라 온 거리(칸). */
    var progress = 0f
    var slowTime = 0f
    var slowFactor = 1f
    var poisonTime = 0f
    var poisonDps = 0f
    var x = 0f
    var y = 0f
}

/** 화면에 잠깐 그리는 공격 선. 좌표는 칸 단위. */
class MazeShot(val kind: MazeGemKind, val fromX: Float, val fromY: Float, val toX: Float, val toY: Float) {
    var life = SHOT_LIFE

    companion object {
        const val SHOT_LIFE = 0.12f
    }
}

/**
 * 보석 미로 — 보석을 놓아 미로를 만들고 적을 막는 타워 디펜스.
 *
 * 라운드: [Phase.Build] 에서 빈 칸을 눌러 무작위 보석을 [PLACEMENTS] 개 놓는다([place]).
 * [Phase.Choose] 에서 그중 하나를 탑으로 남긴다([keep]). 나머지는 돌이 되어 길을 막는다.
 * 같은 종류 · 등급이 둘 이상이면 하나를 한 등급 올려 남길 수 있다([keep] merge).
 * 그러면 [Phase.Wave] — 적이 입구([START])에서 출구([EXIT])까지 가장 짧은 길로 내려오고 탑이 쏜다([step]).
 * 출구에 닿은 적은 생명을 깎는다. 웨이브가 끝나면 다시 Build, 생명이 0이 되면 [Phase.Over].
 */
class GemMazeGame(private val random: Random = Random) {
    enum class Phase { Build, Choose, Wave, Over }

    val rocks = HashSet<Cell>()
    val towers = mutableListOf<MazeGem>()
    /** 이번 라운드에 놓은 보석. */
    val placed = mutableListOf<MazeGem>()
    val enemies = mutableListOf<MazeEnemy>()
    val shots = mutableListOf<MazeShot>()
    /** 지난 [step] 들에서 쏜 탑 종류. 화면이 효과음을 내고 비운다. */
    val fired = mutableListOf<MazeGemKind>()

    var phase = Phase.Build
        private set
    /** 지금 웨이브(또는 다음에 올 웨이브) 번호. */
    var wave = 1
        private set
    var lives = LIVES
        private set
    /** 끝까지 막아 낸 웨이브 수. */
    val cleared: Int get() = wave - 1

    var path: List<Cell> = shortestPath(emptySet()) ?: error("빈 판에 길이 없다")
        private set

    /** 되돌린 보석(종류, 등급) — 다음에 놓을 때 이것부터 그대로 놓는다. 되돌리기로 새로 뽑을 수는 없다. */
    private val returned = ArrayDeque<Pair<MazeGemKind, Int>>()

    /** 이번 라운드에 놓기 · 돌 깨기를 한 순서 — 되돌리기는 마지막 것부터. null 이 아닌 Cell 은 깬 돌. */
    private val history = mutableListOf<Cell?>()

    /** 이번 라운드에 더 깰 수 있는 돌 수. 라운드마다 [BREAKS_PER_ROUND] 로 돌아간다(모아 둘 수 없다). */
    var breaksLeft = BREAKS_PER_ROUND
        private set

    /** 다음에 놓일 보석이 정해져 있으면(되돌린 보석) 그 종류 · 등급. */
    val nextGem: Pair<MazeGemKind, Int>? get() = returned.firstOrNull()

    private var toSpawn = 0
    private var spawnTimer = 0f
    private var nextEnemyId = 0

    fun cellBlocked(cell: Cell) = cell in rocks || towers.any { it.row == cell.row && it.col == cell.col } ||
        placed.any { it.row == cell.row && it.col == cell.col }

    private fun blockedCells(): Set<Cell> = HashSet<Cell>().apply {
        addAll(rocks)
        towers.forEach { add(Cell(it.row, it.col)) }
        placed.forEach { add(Cell(it.row, it.col)) }
    }

    /** 이 칸에 보석을 놓을 수 있는지 — 비어 있고, 입구 · 출구가 아니고, 놓아도 길이 남을 때. */
    fun canPlace(cell: Cell): Boolean {
        if (phase != Phase.Build) return false
        if (cell.row !in 0 until ROWS || cell.col !in 0 until COLS) return false
        if (cell == START || cell == EXIT || cellBlocked(cell)) return false
        return shortestPath(blockedCells() + cell) != null
    }

    /** 놓을 수 있는 칸이 하나라도 있는지. */
    fun anyPlaceable(): Boolean {
        for (row in 0 until ROWS) for (col in 0 until COLS) if (canPlace(Cell(row, col))) return true
        return false
    }

    /** 무작위 보석을 놓는다. 다 놓았거나 놓을 칸이 더 없으면 고르기로 넘어간다. */
    fun place(cell: Cell): MazeGem? {
        if (!canPlace(cell)) return null
        val (kind, level) = returned.removeFirstOrNull()
            ?: (MazeGemKind.entries[random.nextInt(MazeGemKind.entries.size)] to rollLevel())
        val gem = MazeGem(kind, level, cell.row, cell.col)
        placed += gem
        history += null
        path = shortestPath(blockedCells())!!
        if (placed.size >= PLACEMENTS || !anyPlaceable()) phase = Phase.Choose
        return gem
    }

    /** 놓기 중에 돌 하나를 깬다. 길이 넓어지기만 하므로 막힐 걱정은 없다. */
    fun canBreak(cell: Cell) = phase == Phase.Build && breaksLeft > 0 && cell in rocks

    fun breakRock(cell: Cell): Boolean {
        if (!canBreak(cell)) return false
        rocks -= cell
        breaksLeft--
        history += cell
        path = shortestPath(blockedCells())!!
        return true
    }

    /**
     * 이번 라운드의 마지막 행동을 되돌린다(놓기 · 고르기 중에만).
     * 놓은 보석은 거두고 다음에 그대로 다시 놓이게, 깬 돌은 되살리고 깨기 기회를 돌려준다.
     */
    fun canUndo() = (phase == Phase.Build || phase == Phase.Choose) && history.isNotEmpty()

    fun undo(): Boolean {
        if (!canUndo()) return false
        val broken = history.removeAt(history.lastIndex)
        if (broken != null) {
            rocks += broken
            breaksLeft++
        } else {
            val gem = placed.removeAt(placed.lastIndex)
            returned.addFirst(gem.kind to gem.level)
        }
        phase = Phase.Build
        path = shortestPath(blockedCells())!!
        return true
    }

    /** 놓을 칸이 더 없을 때 놓은 것만으로 고르기로 넘어간다(하나도 없으면 바로 웨이브). */
    fun finishPlacing() {
        if (phase != Phase.Build) return
        if (placed.isEmpty()) startWave() else phase = Phase.Choose
    }

    /** 웨이브가 오를수록 높은 등급이 잘 나온다. */
    private fun rollLevel(): Int {
        val w = wave.toFloat()
        val weights = floatArrayOf(100f, 18f + 3f * w, 1f + 1.2f * w, maxOf(0f, 1.2f * (w - 7)), maxOf(0f, 0.8f * (w - 15)))
        var r = random.nextFloat() * weights.sum()
        weights.forEachIndexed { i, weight ->
            r -= weight
            if (r < 0) return i + 1
        }
        return 1
    }

    /** 이 보석과 같은 종류 · 등급인 다른 보석이 이번 라운드에 있는지(합칠 수 있는지). */
    fun canMerge(gem: MazeGem) = gem.level < MAX_LEVEL &&
        placed.any { it !== gem && it.kind == gem.kind && it.level == gem.level }

    /** 놓은 보석 하나를 탑으로 남기고 나머지는 돌로. merge 면 한 등급 올려 남긴다. 그다음 웨이브가 시작된다. */
    fun keep(gem: MazeGem, merge: Boolean = false): Boolean {
        if (phase != Phase.Choose || gem !in placed) return false
        if (merge && !canMerge(gem)) return false
        towers += if (merge) MazeGem(gem.kind, gem.level + 1, gem.row, gem.col) else gem
        placed.filter { it !== gem }.forEach { rocks += Cell(it.row, it.col) }
        placed.clear()
        path = shortestPath(blockedCells())!!
        startWave()
        return true
    }

    private fun startWave() {
        phase = Phase.Wave
        history.clear()
        toSpawn = if (isBossWave) 1 else WAVE_SIZE
        spawnTimer = 0f
        towers.forEach { it.cooldown = 0f }
    }

    val isBossWave: Boolean get() = wave % 10 == 0
    val isFastWave: Boolean get() = !isBossWave && wave % 4 == 0

    /** 이번 웨이브 적 하나의 체력. */
    fun enemyHp(): Float {
        val base = 20f * 1.2f.pow(wave - 1)
        return when {
            isBossWave -> base * 14f
            isFastWave -> base * 0.6f
            else -> base
        }
    }

    /** 웨이브 중 dt 초만큼 진행한다. 웨이브가 끝나면 true. */
    fun step(dt: Float): Boolean {
        if (phase != Phase.Wave) return false
        shots.forEach { it.life -= dt }
        shots.removeAll { it.life <= 0f }

        // 나오기
        if (toSpawn > 0) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                val speed = when {
                    isBossWave -> 0.7f
                    isFastWave -> 2.2f
                    else -> 1.3f
                }
                enemies += MazeEnemy(nextEnemyId++, enemyHp(), speed, isBossWave, isFastWave).also { locate(it) }
                toSpawn--
                spawnTimer = if (isFastWave) 0.45f else 0.8f
            }
        }

        // 움직이기 · 독
        val end = (path.size - 1).toFloat()
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            if (e.slowTime > 0f) e.slowTime -= dt else e.slowFactor = 1f
            if (e.poisonTime > 0f) {
                e.poisonTime -= dt
                e.hp -= e.poisonDps * dt
            }
            if (e.hp <= 0f) {
                iterator.remove()
                continue
            }
            e.progress += e.speed * e.slowFactor * dt
            if (e.progress >= end) {
                iterator.remove()
                lives -= if (e.boss) BOSS_DAMAGE else 1
                continue
            }
            locate(e)
        }
        if (lives <= 0) {
            lives = 0
            phase = Phase.Over
            enemies.clear()
            return true
        }

        // 쏘기 — 사거리 안에서 출구에 가장 가까운 적.
        for (tower in towers) {
            tower.cooldown -= dt
            if (tower.cooldown > 0f) continue
            val target = enemies
                .filter { it.hp > 0f && hypot(it.x - tower.col, it.y - tower.row) <= tower.range }
                .maxByOrNull { it.progress } ?: continue
            tower.cooldown = tower.interval
            hit(tower, target)
            fired += tower.kind
            shots += MazeShot(tower.kind, tower.col.toFloat(), tower.row.toFloat(), target.x, target.y)
        }
        enemies.removeAll { it.hp <= 0f }

        if (toSpawn == 0 && enemies.isEmpty()) {
            wave++
            phase = Phase.Build
            breaksLeft = BREAKS_PER_ROUND
            shots.clear()
            return true
        }
        return false
    }

    private fun hit(tower: MazeGem, target: MazeEnemy) {
        val damage = tower.damage
        target.hp -= damage
        when (tower.kind) {
            MazeGemKind.Fire -> enemies.forEach {
                if (it !== target && hypot(it.x - target.x, it.y - target.y) <= SPLASH) it.hp -= damage * 0.5f
            }
            MazeGemKind.Ice -> {
                target.slowFactor = minOf(target.slowFactor, 0.65f - 0.05f * tower.level)
                target.slowTime = 1.8f
            }
            MazeGemKind.Venom -> {
                target.poisonDps = maxOf(target.poisonDps.takeIf { target.poisonTime > 0f } ?: 0f, damage * 1.3f)
                target.poisonTime = 3f
            }
            MazeGemKind.Bolt -> Unit
        }
    }

    /** 길 위 거리로 칸 좌표를 정한다. */
    private fun locate(e: MazeEnemy) {
        val i = e.progress.toInt().coerceIn(0, path.size - 1)
        val t = e.progress - i
        val a = path[i]
        val b = path[(i + 1).coerceAtMost(path.size - 1)]
        e.x = a.col + (b.col - a.col) * t
        e.y = a.row + (b.row - a.row) * t
    }

    companion object {
        const val COLS = 9
        const val ROWS = 13
        val START = Cell(0, COLS / 2)
        val EXIT = Cell(ROWS - 1, COLS / 2)
        const val PLACEMENTS = 4
        const val BREAKS_PER_ROUND = 1
        const val MAX_LEVEL = 5
        const val LIVES = 20
        const val WAVE_SIZE = 10
        const val BOSS_DAMAGE = 5
        const val SPLASH = 1.0f

        /** 막힌 칸을 피해 입구에서 출구까지 가장 짧은 길(상하좌우). 없으면 null. */
        fun shortestPath(blocked: Set<Cell>): List<Cell>? {
            val previous = HashMap<Cell, Cell>()
            val queue = ArrayDeque(listOf(START))
            val seen = hashSetOf(START)
            while (queue.isNotEmpty()) {
                val cell = queue.removeFirst()
                if (cell == EXIT) {
                    val out = mutableListOf(cell)
                    var c = cell
                    while (c != START) {
                        c = previous.getValue(c)
                        out += c
                    }
                    return out.reversed()
                }
                // 아래 · 위 · 왼쪽 · 오른쪽 순서 — 같은 길이면 곧게 내려가는 길이 먼저.
                for ((dr, dc) in listOf(1 to 0, 0 to -1, 0 to 1, -1 to 0)) {
                    val next = Cell(cell.row + dr, cell.col + dc)
                    if (next.row !in 0 until ROWS || next.col !in 0 until COLS) continue
                    if (next in blocked || !seen.add(next)) continue
                    previous[next] = cell
                    queue.addLast(next)
                }
            }
            return null
        }
    }
}
