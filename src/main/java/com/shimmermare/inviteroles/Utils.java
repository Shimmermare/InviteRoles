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

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.*;
import java.util.Arrays;

public final class Utils
{
    private Utils()
    {
    }

    /**
     * Replace last 3 characters with {@code •}.
     *
     * @param code invite code.
     * @return censored invite code.
     */
    public static String censorInviteCode(String code)
    {
        return code.substring(0, code.length() - 3) + "•••";
    }

    public static MessageEmbed createWarningEmbed(String warning)
    {
        return new EmbedBuilder()
                .setColor(Color.RED)
                .setAuthor("Warning!")
                .setThumbnail("https://cdn.discordapp.com/attachments/630175733708357642/630175767426367508/Exclamation_yellow_128.png")
                .setDescription(warning)
                .setFooter("InviteRole by Shimmermare")
                .build();
    }
}
