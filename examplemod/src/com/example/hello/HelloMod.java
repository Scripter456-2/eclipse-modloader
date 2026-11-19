package com.example.hello;

import dev.eclipse.api.*;

public class HelloMod implements EclipseMod {

    /** A custom event other mods (or you) can post and listen to. */
    public static class GreetEvent extends Event {
        public String message;
        public GreetEvent(String message) { this.message = message; }
    }

    @Override
    public void onInitialize(EclipseAPI api) {
        api.logger().info("Hello from Hello Mod!");

        api.events().subscribe(GreetEvent.class, e -> e.message += " (modded by Eclipse)");

        // Example class patch (needs a bytecode library like ASM to actually change the bytes):
        // api.registerTransformer("net/minecraft/client/Minecraft", bytes -> bytes);

        GreetEvent ev = api.events().post(new GreetEvent("Welcome, player!"));
        api.logger().info(ev.message);
    }

    @Override
    public void onShutdown() {
        System.out.println("[HelloMod] Goodbye!");
    }
}
