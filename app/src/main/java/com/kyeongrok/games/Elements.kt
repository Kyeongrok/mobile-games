package com.kyeongrok.games

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** 주기율표의 원소 하나. 이름은 대한화학회 표기를 따른다. */
data class Element(val number: Int, val symbol: String, val name: String) {
    val category: ElementCategory get() = ElementCategory.of(number)
}

enum class ElementCategory(val label: String, val color: Long) {
    Alkali("알칼리 금속", 0xFFFFB3A7),
    AlkalineEarth("알칼리 토금속", 0xFFFFD8A8),
    Transition("전이 금속", 0xFFFFE9A8),
    PostTransition("전이후 금속", 0xFFCFE8C2),
    Metalloid("준금속", 0xFFB9E4D8),
    Nonmetal("비금속", 0xFFB8DAF5),
    Halogen("할로젠", 0xFFC9C8F2),
    NobleGas("비활성 기체", 0xFFE3C6EE),
    Lanthanide("란타넘족", 0xFFF6C6DA),
    Actinide("악티늄족", 0xFFEFC9C0);

    companion object {
        fun of(number: Int): ElementCategory = when (number) {
            3, 11, 19, 37, 55, 87 -> Alkali
            4, 12, 20, 38, 56, 88 -> AlkalineEarth
            in 57..71 -> Lanthanide
            in 89..103 -> Actinide
            in 21..30, in 39..48, in 72..80, in 104..112 -> Transition
            2, 10, 18, 36, 54, 86, 118 -> NobleGas
            9, 17, 35, 53, 85, 117 -> Halogen
            1, 6, 7, 8, 15, 16, 34 -> Nonmetal
            5, 14, 32, 33, 51, 52 -> Metalloid
            else -> PostTransition
        }
    }
}

object PeriodicTable {
    private const val DATA =
        "H 수소,He 헬륨,Li 리튬,Be 베릴륨,B 붕소,C 탄소,N 질소,O 산소,F 플루오린,Ne 네온," +
            "Na 나트륨,Mg 마그네슘,Al 알루미늄,Si 규소,P 인,S 황,Cl 염소,Ar 아르곤,K 칼륨,Ca 칼슘," +
            "Sc 스칸듐,Ti 타이타늄,V 바나듐,Cr 크로뮴,Mn 망가니즈,Fe 철,Co 코발트,Ni 니켈,Cu 구리,Zn 아연," +
            "Ga 갈륨,Ge 저마늄,As 비소,Se 셀레늄,Br 브로민,Kr 크립톤,Rb 루비듐,Sr 스트론튬,Y 이트륨,Zr 지르코늄," +
            "Nb 나이오븀,Mo 몰리브데넘,Tc 테크네튬,Ru 루테늄,Rh 로듐,Pd 팔라듐,Ag 은,Cd 카드뮴,In 인듐,Sn 주석," +
            "Sb 안티모니,Te 텔루륨,I 아이오딘,Xe 제논,Cs 세슘,Ba 바륨,La 란타넘,Ce 세륨,Pr 프라세오디뮴,Nd 네오디뮴," +
            "Pm 프로메튬,Sm 사마륨,Eu 유로퓸,Gd 가돌리늄,Tb 터븀,Dy 디스프로슘,Ho 홀뮴,Er 어븀,Tm 툴륨,Yb 이터븀," +
            "Lu 루테튬,Hf 하프늄,Ta 탄탈럼,W 텅스텐,Re 레늄,Os 오스뮴,Ir 이리듐,Pt 백금,Au 금,Hg 수은," +
            "Tl 탈륨,Pb 납,Bi 비스무트,Po 폴로늄,At 아스타틴,Rn 라돈,Fr 프랑슘,Ra 라듐,Ac 악티늄,Th 토륨," +
            "Pa 프로트악티늄,U 우라늄,Np 넵투늄,Pu 플루토늄,Am 아메리슘,Cm 퀴륨,Bk 버클륨,Cf 캘리포늄,Es 아인슈타이늄,Fm 페르뮴," +
            "Md 멘델레븀,No 노벨륨,Lr 로렌슘,Rf 러더포듐,Db 더브늄,Sg 시보귬,Bh 보륨,Hs 하슘,Mt 마이트너륨,Ds 다름슈타튬," +
            "Rg 뢴트게늄,Cn 코페르니슘,Nh 니호늄,Fl 플레로븀,Mc 모스코븀,Lv 리버모륨,Ts 테네신,Og 오가네손"

    val ELEMENTS: List<Element> = DATA.split(',').mapIndexed { index, entry ->
        val (symbol, name) = entry.split(' ')
        Element(index + 1, symbol, name)
    }

    fun get(number: Int): Element = ELEMENTS[number - 1]
}

/** 어느 번호까지의 원소에서 뽑을지. */
enum class ElementRange(val label: String, val last: Int) {
    Easy("1~20번", 20),
    Normal("1~36번", 36),
    Hard("1~86번", 86),
    All("전체 118", 118),
}

/** 한 장의 카드. 짝이 되는 두 장은 같은 원소의 기호 카드와 이름 카드다. */
data class ElementCard(val number: Int, val isSymbol: Boolean)

data class ElementMatchGame(
    val cards: List<ElementCard>,
    val matched: Set<Int> = emptySet(),
    /** 지금 앞면이 보이는, 아직 맞추지 못한 카드 위치(최대 두 장). */
    val open: List<Int> = emptyList(),
    val tries: Int = 0,
    val seconds: Int = 0,
    val range: ElementRange,
    /** 가장 최근에 맞춘 원소 번호. */
    val lastMatch: Int? = null,
) {
    val pairs: Int get() = cards.size / 2
    val isComplete: Boolean get() = matched.size == cards.size
    /** 두 장이 펼쳐져 있으면 짝이 아닌 것이다(짝이면 바로 matched로 옮긴다). 뒤집을 때까지 다른 카드를 못 누른다. */
    val isMismatchShowing: Boolean get() = open.size == 2

    fun isFaceUp(index: Int) = index in matched || index in open

    fun flip(index: Int): ElementMatchGame {
        if (isComplete || isMismatchShowing || isFaceUp(index)) return this
        if (open.isEmpty()) return copy(open = listOf(index))
        val first = open.first()
        return if (cards[first].number == cards[index].number) {
            copy(matched = matched + first + index, open = emptyList(), tries = tries + 1, lastMatch = cards[index].number)
        } else {
            copy(open = listOf(first, index), tries = tries + 1)
        }
    }

    fun hideMismatch(): ElementMatchGame = if (isMismatchShowing) copy(open = emptyList()) else this

    companion object {
        fun create(range: ElementRange, pairs: Int, random: Random = Random): ElementMatchGame {
            val numbers = (1..range.last).shuffled(random).take(pairs)
            val cards = numbers.flatMap { listOf(ElementCard(it, true), ElementCard(it, false)) }.shuffled(random)
            return ElementMatchGame(cards, range = range)
        }
    }
}

data class ElementMatchRecord(
    val finishedAt: Long,
    val seconds: Int,
    val tries: Int,
    val pairs: Int,
    val range: ElementRange,
)

object ElementMatchRecords {
    private const val FILE_NAME = "element-match-records.json"

    fun load(context: Context): List<ElementMatchRecord> {
        val array = RecordFile.read(context, FILE_NAME) ?: return emptyList()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            ElementMatchRecord(
                item.getLong("finishedAt"),
                item.getInt("seconds"),
                item.getInt("tries"),
                item.getInt("pairs"),
                ElementRange.entries.find { it.name == item.optString("range") } ?: ElementRange.Easy,
            )
        }
    }

    fun save(context: Context, records: List<ElementMatchRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("finishedAt", record.finishedAt)
                    .put("seconds", record.seconds)
                    .put("tries", record.tries)
                    .put("pairs", record.pairs)
                    .put("range", record.range.name),
            )
        }
        RecordFile.write(context, FILE_NAME, array)
    }
}
