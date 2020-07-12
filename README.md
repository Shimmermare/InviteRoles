## InviteRoles ##
  
Automatically grant roles to users who joined through specific invite.  

### Commands ###
You can specify invite as invite code or as a link.
Role can be specified via mention or it's name (use "quotes" if role has whitespaces in the name). If specified by name, first role with that name will be used.

+ `/ir` will output information about the bot (e.g. version).
+ `/ir settings` will output current guild settings.
+ `/ir settings reset` will reset guild settings to default.
+ `/ir settings <name>` will output current setting value.
+ `/ir settings <name> <value>` will change setting value.
+ `/ir invites` will output invites with roles assigned.
+ `/ir invites <invite>` will output roles assigned to an invite.
+ `/ir invites <invite> clear` will remove all assigned roles from an invite.
+ `/ir invites <invite> add <role>` will assign role to an invite.
+ `/ir invites <invite> remove <role>` will remove role from an invite.

+ To change settings, use **`/ir settings <setting name> <value>`**.
+ **`/ir invite`** will print all invites with the role set.
+ To set role to invite, use **`/ir invite <invite code or link> <role mention or "role name in quotes">`**.
+ To delete invite, use **`/ir invite <invite code or link> remove`**.
+ If there is multiple roles/channels by the same name, the most recently created will be used. Prefer using mention in this case.

### Notes ###

+ Sometimes bot will send a very important warning message into the system message channel. You can disable warnings in settings.
+ To setup invite role you need to have role with *Manage Roles* permission and that role must be higher than the one you giving with invite. In other words, *Moderator* can setup *Member* role, but can't *Moderator* role.  
+ If invite expires or is revoked - it will be removed from bot settings as well.
+ If a role that is the only role if the invite is deleted - invite settings will be removed from bot.
+ **If your server is very popular or you are extremely lucky, it can happen that two or more users join through different invites at the same time. Because Discord doesn't let you know who joined through which invite, all users in question will not receive roles and bot will send warning message. Thankfully, chances are small and situation is purely theoretical.**  
  
### Hosting ###
  
You need bot account ([*How to create and invite bot account?*](https://github.com/reactiflux/discord-irc/wiki/Creating-a-discord-bot-&-getting-a-token)) and Java 11. Bot requires `Manage Server`, `Manage Roles`, `View Channels`, `Send Messages` permissions to work. Start bot with `java -jar InviteRoles-VERSION_HERE.jar` and these arguments:  
+ `--token "your bot token here"` - bot token.  
+ `--db "database_path_here.db"` - SQLite database path.  
  
You can stop bot by using `stop` console command.  
  
### Stack ###

+ **Java 11** and **Kotlin 1.3**
+ **Maven**
+ [**JDA**](https://github.com/DV8FromTheWorld/JDA) - Discord API binding
+ [**Mojang Brigadier**](https://github.com/Mojang/brigadier) - command parsing library
+ [**JOpt-Simple**](https://github.com/jopt-simple/jopt-simple) - argument parsing library
+ [**Logback**](https://github.com/qos-ch/logback) - logging framework (slf4j implementation)
+ **SQLite** file-based database
+ **JUnit 5 Jupiter** and **Mockito** for testing

### Contributions ###

All contributions are welcome. Code style is IntelliJ IDEA default.  
