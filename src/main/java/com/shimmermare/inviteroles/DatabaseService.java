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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;

public class DatabaseService implements AutoCloseable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseService.class);

    private final String path;
    private final Connection connection;

    private DatabaseService(String path, Connection connection)
    {
        this.path = path;
        this.connection = connection;
    }

    /**
     * Check tables and if not exist create.
     *
     * @return {@code true} if everything is ok or {@code false} if something failed.
     */
    public boolean checkTables()
    {
        String createTableInviteRoles = "CREATE TABLE IF NOT EXISTS invite_roles (\n"
                + "server bigint NOT NULL, \n"
                + "invite_code text NOT NULL PRIMARY KEY, \n"
                + "role bigint NOT NULL\n"
                + ");";
        String createTableSettings = "CREATE TABLE IF NOT EXISTS settings (\n"
                + "server bigint NOT NULL PRIMARY KEY, \n"
                + "warnings bit NOT NULL\n"
                + ");";

        try (Statement statement = connection.createStatement())
        {
            statement.addBatch(createTableInviteRoles);
            statement.addBatch(createTableSettings);
            int[] result = statement.executeBatch();

            boolean b = true;
            for (int i = 0; i < result.length; i++)
            {
                int r = result[i];
                if (r == Statement.EXECUTE_FAILED)
                {
                    b = false;
                    LOGGER.error("Create tables sql batch item {} execution failed.", i);
                }
            }
            return b;
        }
        catch (SQLException e)
        {
            LOGGER.error("SQL exception occurred while creating tables.", e);
            return false;
        }
    }

    public ServerSettings getServerSettings(long server)
    {
        ServerSettings settings = new ServerSettings();

        String queryInviteRoles = "SELECT invite_code, role FROM invite_roles WHERE server = ?";
        try (PreparedStatement statement = connection.prepareStatement(queryInviteRoles))
        {
            statement.setLong(1, server);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next())
            {
                settings.setInviteRole(resultSet.getString(1), resultSet.getLong(2));
            }
        }
        catch (SQLException e)
        {
            LOGGER.error("Failed to query invite roles of server {} from database.", server, e);
            return null;
        }

        String querySettings = "SELECT warnings FROM settings WHERE server = ?";
        try (PreparedStatement statement = connection.prepareStatement(querySettings))
        {
            statement.setLong(1, server);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next())
            {
                settings.setWarningsEnabled(resultSet.getBoolean(1));
            }
        }
        catch (SQLException e)
        {
            LOGGER.error("Failed to query settings of server {} from database.", server, e);
            return null;
        }

        return settings;
    }

    public boolean setServerSettings(long server, ServerSettings settings)
    {
        String deleteInviteRoles = "DELETE FROM invite_roles WHERE server = ?;";
        try (PreparedStatement statement = connection.prepareStatement(deleteInviteRoles))
        {
            statement.setLong(1, server);
            statement.execute();
        }
        catch (SQLException e)
        {
            LOGGER.error("Failed to delete invite roles of server {} from database.", server, e);
            return false;
        }

        String insertInviteRoles = "INSERT INTO invite_roles(server, invite_code, role) VALUES(?, ?, ?);";
        Map<String, Long> inviteRoles = settings.getInviteRoles();
        if (!inviteRoles.isEmpty())
        {
            try (PreparedStatement statement = connection.prepareStatement(insertInviteRoles))
            {
                statement.setLong(1, server);
                for (Map.Entry<String, Long> inviteRole : inviteRoles.entrySet())
                {
                    statement.setString(2, inviteRole.getKey());
                    statement.setLong(3, inviteRole.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            catch (SQLException e)
            {
                LOGGER.error("Failed to insert invite roles of server {} to database.", server, e);
                return false;
            }
        }

        String insertOrUpdateSettings = "INSERT INTO settings(server, warnings) VALUES(?, ?)\n" +
                "ON CONFLICT(server)\n" +
                "DO UPDATE SET warnings = ?;";
        try (PreparedStatement statement = connection.prepareStatement(insertOrUpdateSettings))
        {
            statement.setLong(1, server);
            statement.setBoolean(2, settings.isWarningsEnabled());
            statement.setBoolean(3, settings.isWarningsEnabled());
            statement.execute();
        }
        catch (SQLException e)
        {
            LOGGER.error("Failed to insert/update settings of server {} to database.", server, e);
            return false;
        }
        return true;
    }

    public void deleteServerSettings(long server)
    {
        String deleteInviteRoles = "DELETE FROM invite_roles WHERE server = ?;";
        try (PreparedStatement statement = connection.prepareStatement(deleteInviteRoles))
        {
            statement.setLong(1, server);
            statement.execute();
        }
        catch (SQLException e)
        {
            LOGGER.error("Failed to delete invite roles of server {} from database.", server, e);
        }

        String deleteSettings = "DELETE FROM settings WHERE server = ?;";
        try (PreparedStatement statement = connection.prepareStatement(deleteSettings))
        {
            statement.setLong(1, server);
            statement.execute();
        }
        catch (SQLException e)
        {
            LOGGER.error("Failed to delete settings of server {} from database.", server, e);
        }
    }

    @Override
    public void close()
    {
        try
        {
            connection.close();
        }
        catch (SQLException e)
        {
            LOGGER.error("Failed to close database connection.", e);
        }
    }

    /**
     * Connect to SQLite database and create {@link DatabaseService}.
     *
     * @param path sqlite database path.
     * @return service instance or {@code null} if connection attempt failed.
     * @throws IllegalArgumentException if path is {@code null} or empty.
     */
    public static DatabaseService connect(String path)
    {
        if (path == null || path.isEmpty())
        {
            throw new IllegalArgumentException("Path can't be null or empty");
        }
        String url = "jdbc:sqlite:" + path;
        try
        {
            Connection connection = DriverManager.getConnection(url);
            LOGGER.info("Successfully connected to database '{}'.", path);
            return new DatabaseService(path, connection);
        }
        catch (SQLException e)
        {
            LOGGER.error("Failed to open database connection with '{}'.", path, e);
            return null;
        }
    }
}
