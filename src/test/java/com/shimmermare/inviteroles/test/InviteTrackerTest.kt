package com.shimmermare.inviteroles.test

import com.shimmermare.inviteroles.InviteTracker
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.requests.RestAction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

object InviteTrackerTest {
    @Test
    fun tracksNewUses() {
        val before = listOf(
            "code2" to 5,
            "code3" to 2
        )
        val after = listOf(
            "code1" to 1,
            "code2" to 5,
            "code3" to 5
        )
        val result = testCase(before, after)
        assertEquals(mapOf("code1" to 1, "code3" to 3), result)
    }

    @Test
    fun noNegativeDelta() {
        val before = listOf(
            "code1" to 0,
            "code2" to 5
        )
        val after = listOf(
            "code1" to 0
        )
        val result = testCase(before, after)
        Assertions.assertTrue(result.isEmpty())
    }

    private fun testCase(before: List<Pair<String, Int>>, after: List<Pair<String, Int>>): Map<String, Int> {
        val guildMock = mock(Guild::class.java)
        val actionMock = mock(RestAction::class.java)

        doReturn(before.map(this::mockInvite)).`when`(actionMock).complete()
        doReturn(actionMock).`when`(guildMock).retrieveInvites()

        val tracker = InviteTracker(guildMock)

        doReturn(after.map(this::mockInvite)).`when`(actionMock).complete()

        tracker.update()
        return tracker.newUses
    }

    private fun mockInvite(codeToUses: Pair<String, Int>): Invite {
        val mock = mock(Invite::class.java)
        doReturn(codeToUses.first).`when`(mock).code
        doReturn(codeToUses.second).`when`(mock).uses
        return mock
    }
}