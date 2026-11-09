package com.kyeongrok.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ElementMatchTest {
    @Test
    fun periodicTableHas118Elements() {
        assertEquals(118, PeriodicTable.ELEMENTS.size)
        assertEquals("Fe", PeriodicTable.get(26).symbol)
        assertEquals("철", PeriodicTable.get(26).name)
        assertEquals("Og", PeriodicTable.get(118).symbol)
        assertEquals(118, PeriodicTable.ELEMENTS.map { it.symbol }.toSet().size)
        assertEquals("Iron", PeriodicTable.get(26).english)
        assertEquals("Nitrogen", PeriodicTable.get(7).english)
        assertEquals("Oganesson", PeriodicTable.get(118).english)
    }

    @Test
    fun createDealsSymbolAndNameCardForEachElement() {
        val game = ElementMatchGame.create(ElementRange.Easy, 8, Random(1))
        assertEquals(16, game.cards.size)
        game.cards.groupBy { it.number }.forEach { (number, cards) ->
            assertTrue(number in 1..20)
            assertEquals(setOf(true, false), cards.map { it.isSymbol }.toSet())
        }
    }

    @Test
    fun matchingAndMismatching() {
        var game = ElementMatchGame.create(ElementRange.Easy, 6, Random(2))
        val first = 0
        val partner = game.cards.indices.first { it != first && game.cards[it].number == game.cards[first].number }
        val other = game.cards.indices.first { game.cards[it].number != game.cards[first].number }

        game = game.flip(first).flip(other)
        assertTrue(game.isMismatchShowing)
        assertEquals(game, game.flip(partner)) // 덮기 전에는 못 누른다
        game = game.hideMismatch()

        game = game.flip(first).flip(partner)
        assertEquals(setOf(first, partner), game.matched)
        assertEquals(game.cards[first].number, game.lastMatch)
        assertEquals(2, game.tries)
        assertFalse(game.isComplete)
    }
}
