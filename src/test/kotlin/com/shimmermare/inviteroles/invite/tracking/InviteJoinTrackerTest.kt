package com.shimmermare.inviteroles.invite.tracking

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.requests.RestAction
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class InviteJoinTrackerTest {
    @Test
    fun `one of multiple existing invites was used`() {
        val inviteA = mockInvite("a", 0)
        val inviteB = mockInvite("b", 5)
        val invites = listOf(inviteA, inviteB)
        val guild = mockGuild(invites)

        val tracker = TrackerWrapper(guild)

        doReturn(1).`when`(inviteA).uses

        val member = mockMember(guild)
        val event = createEvent(member)
        tracker.tracker.onGuildMemberJoin(event)

        assertTrue(tracker.onJoinSureResult[member] == "a")
        assertTrue(tracker.onJoinUnsureResult.isEmpty())
    }

    @Test
    fun `non-existent before invite was used`() {
        val invite = mockInvite("existent", 5)
        val invites = mutableListOf(invite)
        val guild = mockGuild(invites)

        val tracker = TrackerWrapper(guild)

        val newInvite = mockInvite("new", 1)
        invites.add(newInvite)

        val member = mockMember(guild)
        val event = createEvent(member)
        tracker.tracker.onGuildMemberJoin(event)

        assertTrue(tracker.onJoinSureResult[member] == "new")
        assertTrue(tracker.onJoinUnsureResult.isEmpty())
    }

    @Test
    fun `max uses of an invite was reached`() {
        val inviteA = mockInvite("a", 9)
        val inviteB = mockInvite("b", 5)
        val invites = mutableListOf(inviteA, inviteB)
        val guild = mockGuild(invites)

        val tracker = TrackerWrapper(guild)

        invites.remove(inviteA)

        val member = mockMember(guild)
        val event = createEvent(member)
        tracker.tracker.onGuildMemberJoin(event)

        assertTrue(tracker.onJoinSureResult[member] == "a")
        assertTrue(tracker.onJoinUnsureResult.isEmpty())
    }

    @Test
    fun `multiple invites were used`() {
        val inviteA = mockInvite("a", 0)
        val inviteB = mockInvite("b", 5)
        val inviteC = mockInvite("c", 1)
        val invites = listOf(inviteA, inviteB, inviteC)
        val guild = mockGuild(invites)

        val tracker = TrackerWrapper(guild)

        doReturn(1).`when`(inviteA).uses
        doReturn(6).`when`(inviteB).uses

        val member = mockMember(guild)
        val event = createEvent(member)
        tracker.tracker.onGuildMemberJoin(event)

        assertTrue(tracker.onJoinSureResult.isEmpty())
        assertTrue(tracker.onJoinUnsureResult[member]?.toSet() == setOf("a", "b"))
    }

    @Test
    fun `multiple non-existent before invites were used`() {
        val inviteA = mockInvite("a", 0)
        val invites = mutableListOf(inviteA)
        val guild = mockGuild(invites)

        val tracker = TrackerWrapper(guild)

        val inviteB = mockInvite("b", 1)
        val inviteC = mockInvite("c", 1)
        invites.add(inviteB)
        invites.add(inviteC)

        val member = mockMember(guild)
        val event = createEvent(member)
        tracker.tracker.onGuildMemberJoin(event)

        assertTrue(tracker.onJoinSureResult.isEmpty())
        assertTrue(tracker.onJoinUnsureResult[member]?.toSet() == setOf("b", "c"))
    }

    @Test
    fun `multiple invites reached max uses`() {
        val inviteA = mockInvite("a", 0)
        val inviteB = mockInvite("b", 9)
        val inviteC = mockInvite("c", 5)
        val invites = mutableListOf(inviteA, inviteB, inviteC)
        val guild = mockGuild(invites)

        val tracker = TrackerWrapper(guild)

        invites.remove(inviteB)
        invites.remove(inviteC)

        val member = mockMember(guild)
        val event = createEvent(member)
        tracker.tracker.onGuildMemberJoin(event)

        assertTrue(tracker.onJoinSureResult.isEmpty())
        assertTrue(tracker.onJoinUnsureResult[member]?.toSet() == setOf("b", "c"))
    }

    @Test
    fun `some non-existent invites were used and some invites reached max uses`() {
        val inviteA = mockInvite("a", 0)
        val inviteB = mockInvite("b", 9)
        val invites = mutableListOf(inviteA, inviteB)
        val guild = mockGuild(invites)

        val tracker = TrackerWrapper(guild)

        invites.remove(inviteB)
        val inviteC = mockInvite("c", 1)
        invites.add(inviteC)

        val member = mockMember(guild)
        val event = createEvent(member)
        tracker.tracker.onGuildMemberJoin(event)

        assertTrue(tracker.onJoinSureResult.isEmpty())
        assertTrue(tracker.onJoinUnsureResult[member]?.toSet() == setOf("b", "c"))
    }

    private fun mockInvite(code: String, uses: Int): Invite {
        val invite = mock(Invite::class.java)
        doReturn(code).`when`(invite).code
        doReturn(uses).`when`(invite).uses
        return invite
    }

    private fun mockGuild(invites: List<Invite>): Guild {
        @Suppress("UNCHECKED_CAST")
        val restAction = mock(RestAction::class.java) as RestAction<List<Invite>>
        doReturn(invites).`when`(restAction).complete()
        val guild = mock(Guild::class.java)
        doReturn(restAction).`when`(guild).retrieveInvites()
        return guild
    }

    private fun mockMember(guild: Guild): Member {
        val member = mock(Member::class.java)
        doReturn(guild).`when`(member).guild
        return member
    }

    private fun createEvent(member: Member): GuildMemberJoinEvent {
        return GuildMemberJoinEvent(mock(JDA::class.java), 0, member)
    }

    private class TrackerWrapper(
            guild: Guild
    ) {
        val onJoinSureResult = HashMap<Member, String>()
        val onJoinUnsureResult = HashMap<Member, List<String>>()
        val tracker = InviteJoinTracker(
                guild,
                onJoinSure = { m, i -> onJoinSureResult.put(m, i) },
                onJoinUnsure = { m, i -> onJoinUnsureResult.put(m, i) }
        )
    }
}