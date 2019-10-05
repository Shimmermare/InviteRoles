/*
 * MIT License
 *
 * Copyright (c) 2019 Shimmermare
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.shimmermare.inviteroles;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.utils.MiscUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.shimmermare.inviteroles.Utils.censorInviteCode;

/**
 * Bot commands class.
 * <p>
 * Brigadier requires to return magic int after command is finished.
 * Convention for this is:
 * Right half of low 16 bits is sub-command id.
 * Left half or high 16 bits is result of sub-command.
 */
public final class Command
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Command.class);

    private Command()
    {

    }

    static void register(CommandDispatcher<CommandSource> commandDispatcher)
    {
        commandDispatcher.register(literal("inviteroles")
                .then(literal("warnings")
                        .requires(s -> s.getMember().hasPermission(Permission.ADMINISTRATOR))
                        .then(literal("on").executes(c -> warningsSet(c, true)))
                        .then(literal("enable").executes(c -> warningsSet(c, true)))
                        .then(literal("off").executes(c -> warningsSet(c, false)))
                        .then(literal("disable").executes(c -> warningsSet(c, false)))
                        .executes(Command::warningsStatus)
                )
                .then(argument("invite-code", word())
                        .requires(s -> s.getMember().hasPermission(Permission.MANAGE_ROLES))
                        .then(literal("remove")
                                .executes(Command::inviteRoleRemove)
                        )
                        .then(argument("role", RoleArgumentType.role())
                                .executes(Command::inviteRoleSet)
                        )
                        .executes(Command::inviteRoleNoArg)
                )
                .executes(Command::executeNoArg)
        );
    }

    private static int warningsSet(CommandContext<CommandSource> context, boolean enabled)
    {
        CommandSource source = context.getSource();
        Member member = source.getMember();
        TextChannel channel = source.getChannel();
        Guild server = channel.getGuild();
        ServerInstance instance = source.getServerInstance();
        ServerSettings settings = instance.getServerSettings();

        settings.setWarningsEnabled(enabled);
        channel.sendMessage("Warnings now `" + (enabled ? "enabled" : "disabled") + "`.").queue();
        LOGGER.debug("Warnings on server {} were set to {} by user {}.",
                server.getIdLong(), enabled, member.getIdLong());
        return 5;
    }

    private static int warningsStatus(CommandContext<CommandSource> context)
    {
        CommandSource source = context.getSource();
        Member member = source.getMember();
        TextChannel channel = source.getChannel();
        Guild server = channel.getGuild();
        ServerInstance instance = source.getServerInstance();
        ServerSettings settings = instance.getServerSettings();

        channel.sendMessage("Warnings are `" + (settings.isWarningsEnabled() ? "enabled" : "disabled") + "`.").queue();
        LOGGER.debug("Warnings status on server {} requested by user {}", server.getIdLong(), member.getIdLong());
        return 4;
    }

    private static int inviteRoleRemove(CommandContext<CommandSource> context)
    {
        CommandSource source = context.getSource();
        Member member = source.getMember();
        TextChannel channel = source.getChannel();
        Guild server = channel.getGuild();
        ServerInstance instance = source.getServerInstance();
        ServerSettings settings = instance.getServerSettings();

        String inviteCode = context.getArgument("invite-code", String.class);

        long role = settings.removeInviteRole(inviteCode);
        if (role == 0)
        {
            channel.sendMessage("There is no invite role for invite `" + censorInviteCode(inviteCode) + "` set.").queue();
            LOGGER.debug("User {} from server {} tried to remove non-existent invite role for invite {}",
                    member.getIdLong(), server.getIdLong(), inviteCode);
            return 1 << 16 | 3;
        }

        channel.sendMessage("Invite role for invite `" + censorInviteCode(inviteCode) + "` was removed.").queue();
        LOGGER.debug("Invite role {}/{} was removed from server {} by user {}",
                inviteCode, role, server.getIdLong(), member.getIdLong());
        return 3;
    }

    private static int inviteRoleSet(CommandContext<CommandSource> context)
    {
        CommandSource source = context.getSource();
        Member member = source.getMember();
        TextChannel channel = source.getChannel();
        Guild server = channel.getGuild();
        ServerInstance instance = source.getServerInstance();
        ServerSettings settings = instance.getServerSettings();

        String inviteCode = context.getArgument("invite-code", String.class);
        RoleRetriever roleRetriever = context.getArgument("role", RoleRetriever.class);

        Role role = roleRetriever.apply(server);
        if (role == null)
        {
            channel.sendMessage("Role in `role` argument doesn't exist! " +
                    "Don't forget to use \"quotes\" if role name contains whitespaces.").queue();
            LOGGER.debug("User {} from server {} tried to set non-existent role for invite {}",
                    member.getIdLong(), server.getIdLong(), inviteCode);
            return 3 << 16 | 2;
        }
        if (!doesInviteExists(server, inviteCode))
        {
            channel.sendMessage("Invite by code `" + censorInviteCode(inviteCode) + "` doesn't exist!").queue();
            LOGGER.debug("User {} from server {} tried to set invite role {} for non-existent invite {}",
                    member.getIdLong(), server.getIdLong(), role.getIdLong(), inviteCode);
            return 4 << 16 | 2;
        }

        settings.setInviteRole(inviteCode, role.getIdLong());
        channel.sendMessage("Invite role `" + role.getName() + "` for invite `" + censorInviteCode(inviteCode) + "` was set.").queue();
        LOGGER.debug("Invite role {}/{} was set by user {} from server {}",
                inviteCode, role.getIdLong(), member.getIdLong(), server.getIdLong());
        return 2;
    }

    private static int inviteRoleNoArg(CommandContext<CommandSource> context)
    {
        CommandSource source = context.getSource();
        Member member = source.getMember();
        TextChannel channel = source.getChannel();
        Guild server = channel.getGuild();
        ServerInstance instance = source.getServerInstance();
        ServerSettings settings = instance.getServerSettings();

        String inviteCode = context.getArgument("invite-code", String.class);

        long roleId = settings.getInviteRole(inviteCode);
        if (roleId == 0)
        {
            channel.sendMessage("No invite role is set for `" + Utils.censorInviteCode(inviteCode) + "`.").queue();
            return 1 << 16 | 7;
        }
        Role role = server.getRoleById(roleId);
        if (role == null)
        {
            channel.sendMessage("**Error**: invite role for `" + Utils.censorInviteCode(inviteCode)
                    + "` is set to id `" + roleId + "` but such role doesn't exist.").queue();
            LOGGER.error("Unexpectedly invite role {} doesn't exist on server {}", roleId, server.getIdLong());
            return 2 << 16 | 7;
        }

        channel.sendMessage("Invite role for `" + Utils.censorInviteCode(inviteCode)
                + "` is `" + role.getName() + "`.").queue();
        LOGGER.debug("Invite role info command for invite {} issued from server {} by user {}.",
                inviteCode, server.getIdLong(), member.getIdLong());
        return 7;
    }

    private static int executeNoArg(CommandContext<CommandSource> context)
    {
        CommandSource source = context.getSource();
        Member member = source.getMember();
        TextChannel channel = source.getChannel();
        Guild server = channel.getGuild();
        ServerInstance instance = source.getServerInstance();
        ServerSettings settings = instance.getServerSettings();

        StringBuilder builder = new StringBuilder();

        builder.append("Warnings: ").append(settings.isWarningsEnabled() ? "enabled" : "disabled").append('.');

        Map<String, Long> inviteRoles = settings.getInviteRoles();
        if (inviteRoles.isEmpty())
        {
            builder.append("\nNo invite roles set!");
        }
        else
        {
            for (Map.Entry<String, Long> inviteRole : inviteRoles.entrySet())
            {
                Role role = server.getRoleById(inviteRole.getValue());
                builder.append("\n• ")
                        .append(censorInviteCode(inviteRole.getKey()))
                        .append(" / @")
                        .append(role == null ? "deleted-role" : role.getName());
            }
        }

        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setAuthor("Current Settings")
                .setColor(Color.MAGENTA)
                .setDescription(builder)
                .setFooter("InviteRole by Shimmermare");

        channel.sendMessage(embedBuilder.build()).queue();
        LOGGER.debug("Settings command executed from server {} by user {}.",
                server.getIdLong(), member.getIdLong());
        return 1;
    }

    private static boolean doesInviteExists(Guild server, String code)
    {
        //blocking but since it's in command I think it's okay
        return server.retrieveInvites().complete().stream().anyMatch(i -> i.getCode().equals(code));
    }

    private static LiteralArgumentBuilder<CommandSource> literal(String name)
    {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<CommandSource, T> argument(String name, ArgumentType<T> type)
    {
        return RequiredArgumentBuilder.argument(name, type);
    }

    /**
     * A little interface to get around java type erasure.
     *
     * @see RoleArgumentType
     */
    private interface RoleRetriever extends Function<Guild, Role>
    {
    }

    /**
     * Despite the name, doesn't represent {@link Role} by itself
     * but rather provides a function to get it from server.
     * <p>
     * Argument may be role mention aka "<@&snowflake_id>" or role name (case insensitive).
     * In latter case the most recently created will be used.
     */
    private static final class RoleArgumentType implements ArgumentType<RoleRetriever>
    {
        private RoleArgumentType()
        {

        }

        static RoleArgumentType role()
        {
            return new RoleArgumentType();
        }

        @Override
        public RoleRetriever parse(StringReader reader) throws CommandSyntaxException
        {
            final int startPos = reader.getCursor();
            if (reader.read() == '<' && reader.read() == '@' && reader.read() == '&')
            {
                String idStr = reader.readStringUntil('>');
                //better parse now so exception won't be thrown later
                long id = MiscUtil.parseSnowflake(idStr);
                return s -> s.getRoleById(id);
            }
            else
            {
                reader.setCursor(startPos); //don't forget to reset cursor
                String arg = reader.readString();
                return s ->
                {
                    List<Role> rolesByName = s.getRolesByName(arg, true);
                    return rolesByName.stream().max(Comparator.comparing(ISnowflake::getTimeCreated)).orElse(null);
                };
            }
        }
    }

    /**
     * A little interface to get around java type erasure.
     *
     * @see TextChannelArgumentType
     */
    private interface TextChannelRetriever extends Function<Guild, TextChannel>
    {
    }

    /**
     * Despite the name, doesn't represent {@link TextChannel} by itself
     * but rather provides a function to get it from server.
     * <p>
     * Argument may be channel mention aka "<#snowflake_id>" or channel name (case insensitive).
     * In latter case the most recently created will be used.
     */
    private static final class TextChannelArgumentType implements ArgumentType<TextChannelRetriever>
    {
        private TextChannelArgumentType()
        {

        }

        static TextChannelArgumentType channel()
        {
            return new TextChannelArgumentType();
        }

        @Override
        public TextChannelRetriever parse(StringReader reader) throws CommandSyntaxException
        {
            final int startPos = reader.getCursor();
            if (reader.read() == '<' && reader.read() == '#')
            {
                String idStr = reader.readStringUntil('>');
                //better parse now so exception won't be thrown later
                long id = MiscUtil.parseSnowflake(idStr);
                return s -> s.getTextChannelById(id);
            }
            else
            {
                reader.setCursor(startPos); //don't forget to reset cursor
                String arg = reader.readString();
                return s ->
                {
                    List<TextChannel> channelsByName = s.getTextChannelsByName(arg, true);
                    return channelsByName.stream().max(Comparator.comparing(ISnowflake::getTimeCreated)).orElse(null);
                };
            }
        }
    }
}
