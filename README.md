## InviteRoles ##
  
Automatically grant roles to users who joined through specific invite.  
  
+ To setup, just use **`/inviteroles <invite_code> <role>`** - now everyone joining through this invite will receive the role.  
+ Use **`/inviteroles <invite_code> remove`** to disable.
+ **`/inviteroles`** without arguments will output current settings.  
+ Sometimes bot will send a very important warning message into system message channel. If you don't want to see warnings, use **`/inviteroles warnings off`** to disable (and **`on`** to enable).  
+ You can use either mention or "quoted name" to specify role and channel arguments.  
+ If there is multiple roles/channels by the same name, the most recently created will be used. Prefer using mention in this case.  
  
**Note: to setup invite role you need to have role with *Manage Roles* permission and that role must be higher than the one you giving with invite.**  
In other words, *Moderator* can setup *Member* role, but can't *Moderator* role.  
  
**Note: if invite expires or is revoked, setting for it will be deleted.**  

**Note: if your server is very popular or you are extremely lucky, it can happen that two or more users join through different invites at the same time. Because Discord doesn't let you know who joined through which invite, all users in question will not receive roles and bot will send warning message. Thankfully, chances are small and situation is purely theoretical.**  
  
### Hosting ###
  
You can invite bot **[here](https://discordapp.com/oauth2/authorize?&client_id=630112341987688492&scope=bot&permissions=268438560)** or self-host it. To do that you need bot account ([*How to create and invite bot account?*](https://github.com/reactiflux/discord-irc/wiki/Creating-a-discord-bot-&-getting-a-token)) and Java 8. Bot requires `Manage Server`, `Manage Roles`, `View Channels`, `Send Messages` permissions to work. Start bot with `java -jar InviteRoles-VERSION_HERE.jar` and these arguments:  
+ `--token "your bot token here"` - bot token.  
+ `--db-path "jdbc:sqlite:database_path_here.db"` - SQLite database path.  
+ `--admin user_id` - Discord user who will receive all bot PMs and can send custom warnings to the joined servers. Optional.  
  
To exit application press ENTER.  
  
### Stack ###
  
+ Java 8  
+ Maven  
+ [JDA](https://github.com/DV8FromTheWorld/JDA) Discord API binding.  
+ [Mojang Brigadier](https://github.com/Mojang/brigadier) command parsing library.  
+ [JOpt-Simple](https://github.com/jopt-simple/jopt-simple) start arg parsing library.  
+ [logback](https://github.com/qos-ch/logback) logging framework.  
+ SQLite
+ IntelliJ IDEA Community 2019  
  
### Contributions ###  
  
All contributions are welcome. Just follow the conventions.  