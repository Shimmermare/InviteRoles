## InviteRoles ##

Automatically grant roles to users who joined through specific invite.  

+ To setup, just use **`/inviteroles <invite_code> <role>`** - now everyone joining through this invite will receive the role.  
+ Use **`/inviteroles <invite_code> remove`** to disable.
+ **`/inviteroles`** without arguments will output current settings.  
+ Sometimes bot will send a very important warning message into system message channel. If you don't want to see warnings, use **`/inviteroles warnings off`** to disable (and **`on`** to enable).  
+ You can use either mention or "quoted name" to specify role and channel arguments.  
+ If there is multiple roles/channels by the same name, the most recently created will be used. Prefer using mention in this case.  
  
**Note: to setup role giving you need to have on you role with *Manage Roles* permission and that role must be higher than the one you giving with invite.**  
In other words, *Moderator* can setup *Member* role, but can't *Moderator* role.  

**Note: if invite expires or is revoked, setting for it will be deleted.**  

**Note: if your server is very popular or you extremely lucky, it can happen that two or more users join through different invites at the same second. Because Discord doesn't let you know who joined through which invite, all users in question will not receive roles and bot will send warning message to log channel. Thankfully, chances are very small and situation is purely theoretical.**  

### Hosting ###

You can invite bot **[here]()** or self-host it. Make sure you have right Java 8 installed and start bot with `java -jar InviteRoles-VERSION_HERE.jar --token "your bot token here"`. To exit press ENTER.  

### Stack ###

+ Java 8  
+ Maven  
+ [JDA](https://github.com/DV8FromTheWorld/JDA) Discord API binding.  
+ [Mojang Brigadier](https://github.com/Mojang/brigadier) command parsing library.  
+ [JOpt-Simple](https://github.com/jopt-simple/jopt-simple) start arg parsing library.  
+ [logback](https://github.com/qos-ch/logback) logging framework.  
+ IntelliJ IDEA Community 2019  

### Contributions ###  

All contributions are welcome. Just follow the conventions.  