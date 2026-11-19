# Eclipse - a simple Minecraft (Java) modloader

Eclipse is a Java agent. It runs before Minecraft starts, loads mod jars from a `mods/` folder,
resolves dependencies, and gives each mod an event bus plus a bytecode-transformer hook.

## Install
1. Copy `eclipse.jar` anywhere (e.g. next to your game folder) and make a `mods/` folder in the game directory.
2. Put mod jars in `mods/`.
3. Add this to your launcher's **JVM arguments**:

       -javaagent:C:/path/to/eclipse.jar

   Optional custom mods folder: `-Declipse.mods=C:/path/to/mods`

## Make a mod
1. Implement `dev.eclipse.api.EclipseMod` (see `examplemod/`).
2. Add `eclipse.mod.properties` at the root of your jar:

       id=my_mod
       name=My Mod
       version=1.0.0
       entrypoint=com.me.MyMod
       dependencies=other_mod,another_mod

3. Compile against `eclipse.jar`, jar it, drop it in `mods/`.

## API
- `api.events().subscribe(MyEvent.class, e -> ..., priority)` / `api.events().post(event)`
- `api.registerTransformer("net/minecraft/client/Minecraft", bytes -> newBytes)`
  patches a class as it loads (bring ASM/Javassist inside your mod jar to edit the bytes)
- `api.isModLoaded("id")`, `api.logger()`, `api.modId()`
- Extend `Event` and call `cancel()` for cancellable events.

## Build from source (JDK 17+)
    ./build.sh

## Notes
- Eclipse is the loader only. It does not ship Minecraft hooks or events by itself; mods hook Minecraft via
  transformers. Pre-1.21 Minecraft class names are obfuscated, so use the right names for your version.
- Works with the vanilla launcher, since it only needs a JVM argument.
